"""Backend-to-backend Purchase Request endpoints."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import PurchaseRequest
from ..schemas import PurchaseRequestInvoiceContext, PurchaseRequestInvoiceOption
from ..security import require_pr_integration_key


router = APIRouter(
    prefix="/integration/purchase-requests",
    tags=["integration"],
    dependencies=[Depends(require_pr_integration_key)],
)


@router.get("/invoice-options", response_model=list[PurchaseRequestInvoiceOption])
def list_requests(
    db: Session = Depends(get_db),
) -> list[PurchaseRequest]:
    return (
        db.query(PurchaseRequest)
        .filter(PurchaseRequest.request_approval_status == "approved")
        .order_by(PurchaseRequest.id.desc())
        .all()
    )


@router.get(
    "/{request_code}/invoice-context",
    response_model=PurchaseRequestInvoiceContext
)
def get_invoice_context(
    request_code: str,
    db: Session = Depends(get_db)
) -> PurchaseRequest:
    pr = (
        db.query(PurchaseRequest)
        .filter(PurchaseRequest.request_code == request_code)
        .one_or_none()
    )

    if pr is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Not found")

    if pr.request_approval_status != "approved":
        raise HTTPException(status.HTTP_409_CONFLICT, "Purchase request is not approved")
    return pr
