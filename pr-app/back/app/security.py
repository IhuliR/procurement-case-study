"""Password hashing + opaque-token session helpers.

This is deliberately simple: no JWT, no refresh, cookie token persisted in the
DB. Good for a case study, bad for production."""
from __future__ import annotations

import os
import secrets

from fastapi import Cookie, Depends, Header, HTTPException, Response, status
from passlib.context import CryptContext
from sqlalchemy.orm import Session

from .db import get_db
from .models import Session as SessionModel
from .models import User

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

COOKIE_NAME = "pr_token"

INTEGRATION_KEY_HEADER = "X-Integration-Key"


def hash_password(plain: str) -> str:
    return pwd_context.hash(plain)


def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)


def issue_session(db: Session, user: User, response: Response) -> str:
    token = secrets.token_urlsafe(32)
    db.add(SessionModel(token=token, user_id=user.id))
    db.commit()
    # In dev we run front+back on different ports — relax SameSite.
    response.set_cookie(
        COOKIE_NAME,
        token,
        httponly=True,
        samesite="lax",
        secure=False,
        max_age=60 * 60 * 8,
    )
    return token


def clear_session(db: Session, token: str | None, response: Response) -> None:
    if token:
        db.query(SessionModel).filter_by(token=token).delete()
        db.commit()
    response.delete_cookie(COOKIE_NAME)


def current_user(
    pr_token: str | None = Cookie(default=None),
    db: Session = Depends(get_db),
) -> User:
    if not pr_token:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Not authenticated")
    session = db.get(SessionModel, pr_token)
    if not session:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid session")
    user = db.get(User, session.user_id)
    if not user:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "User no longer exists")
    return user


def require_role(*allowed: str):
    def _inner(user: User = Depends(current_user)) -> User:
        if user.role not in allowed:
            raise HTTPException(status.HTTP_403_FORBIDDEN, f"Requires role: {', '.join(allowed)}")
        return user
    return _inner


def require_pr_integration_key(
    integration_key: str | None = Header(
        default=None,
        alias=INTEGRATION_KEY_HEADER,
    ),
) -> None:
    configured_key = os.environ.get("PR_INTEGRATION_API_KEY")

    if (
        not configured_key
        or not integration_key
        or not secrets.compare_digest(integration_key, configured_key)
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid integration key"
        )
