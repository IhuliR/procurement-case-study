"""Purchase request CRUD + status transitions + PDF export."""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import Response
from sqlalchemy import func
from sqlalchemy.orm import Session

from ..db import get_db
from ..integrations.invoice import (
    InvoiceClient,
    InvoiceIntegrationNotConfiguredError,
    InvoiceInvalidResponseError,
    InvoiceUnavailableError,
    get_invoice_client,
)
from ..models import PurchaseRequest, User
from ..pdf import render_pr_pdf
from ..schemas import (
    InvoiceSummary,
    PRStatus,
    PurchaseRequestCreate,
    PurchaseRequestOut,
    PurchaseRequestUpdate,
)
from ..security import current_user

router = APIRouter(prefix="/purchase-request", tags=["purchase-requests"])


def _next_code(db: Session) -> str:
    """Generate PR-{n}. Not concurrency-safe — flag this in your review."""
    highest = db.query(func.max(PurchaseRequest.id)).scalar() or 0
    return f"PR-{highest + 1}"


def _can_transition(user: User, pr: PurchaseRequest, new_status: PRStatus) -> bool:
    current = pr.request_approval_status
    if current == new_status:
        return True
    if current in {"approved", "rejected"}:
        return False  # terminal
    if current == "initiated" and new_status == "sent for approval":
        return pr.request_author == user.username
    if current == "sent for approval" and new_status in {"approved", "rejected"}:
        return user.role == "finance"
    return False


@router.get("", response_model=list[PurchaseRequestOut])
def list_prs(
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> list[PurchaseRequest]:
    q = db.query(PurchaseRequest).order_by(PurchaseRequest.id.desc())
    # Employees see everything (the original system worked that way). Finance
    # users also see everything. A future "only my requests" filter belongs in
    # query params, not here.
    return q.all()


# Note: declared on the parent router as a sibling path so the URL stays
# `/purchase-request-new` — that's what the spec asked for.
@router.post(
    "-new",
    response_model=PurchaseRequestOut,
    status_code=status.HTTP_201_CREATED,
)
def create_pr(
    payload: PurchaseRequestCreate,
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> PurchaseRequest:
    pr = PurchaseRequest(
        request_author=user.username,
        author_id=user.id,
        request_name=payload.request_name,
        request_code=_next_code(db),
        supplier_name=payload.supplier_name,
        supplier_email=payload.supplier_email,
        request_details=payload.request_details,
        request_approval_status="initiated",
    )
    db.add(pr)
    db.commit()
    db.refresh(pr)
    return pr


@router.put("/{pr_id}", response_model=PurchaseRequestOut)
def update_pr(
    pr_id: int,
    payload: PurchaseRequestUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> PurchaseRequest:
    pr = db.get(PurchaseRequest, pr_id)
    if not pr:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Not found")

    # Field edits are only allowed while the request is editable AND only by
    # the author. Status changes are role-gated separately.
    editable_fields = {"request_name", "supplier_name", "supplier_email", "request_details"}
    requested_field_changes = {
        k for k in editable_fields if getattr(payload, k) is not None
    }
    if requested_field_changes:
        if pr.request_author != user.username:
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Only the author can edit fields")
        if pr.request_approval_status != "initiated":
            raise HTTPException(
                status.HTTP_409_CONFLICT,
                "Request can only be edited while status is 'initiated'",
            )
        for k in requested_field_changes:
            setattr(pr, k, getattr(payload, k))

    if payload.request_approval_status is not None:
        if not _can_transition(user, pr, payload.request_approval_status):
            raise HTTPException(
                status.HTTP_409_CONFLICT,
                f"Cannot transition {pr.request_approval_status} → {payload.request_approval_status} as {user.role}",
            )
        pr.request_approval_status = payload.request_approval_status

    db.commit()
    db.refresh(pr)
    return pr


@router.get("/{pr_id}/pdf")
def export_pdf(
    pr_id: int,
    db: Session = Depends(get_db),
    _: User = Depends(current_user),
) -> Response:
    pr = db.get(PurchaseRequest, pr_id)
    if not pr:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Not found")
    pdf_bytes = render_pr_pdf(pr)
    return Response(
        content=pdf_bytes,
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{pr.request_code}.pdf"'},
    )


@router.get(
    "/{request_code}/invoices",
    response_model=list[InvoiceSummary],
)
def list_pr_invoices(
    request_code: str,
    db: Session = Depends(get_db),
    _: User = Depends(current_user),
    invoice_client: InvoiceClient = Depends(get_invoice_client),
) -> list[InvoiceSummary]:
    pr = (
        db.query(PurchaseRequest)
        .filter(PurchaseRequest.request_code == request_code)
        .one_or_none()
    )
    if not pr:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Purchase request not found",
        )
    try:
        return invoice_client.get_invoices(pr.request_code)
    except (
        InvoiceUnavailableError,
        InvoiceIntegrationNotConfiguredError,
    ) as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(error),
        ) from None
    except InvoiceInvalidResponseError as error:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(error)
        ) from None
