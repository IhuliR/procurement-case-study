"""Tests for displaying related invoices through the PR backend."""
from decimal import Decimal
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.integrations.invoice import (
    InvoiceClient,
    InvoiceIntegrationNotConfiguredError,
    InvoiceInvalidResponseError,
    InvoiceUnavailableError,
    get_invoice_client,
)
from app.main import app
from app.models import PurchaseRequest, User
from app.schemas import InvoiceSummary
from app.security import current_user


def create_purchase_request(
    db_session: Session,
    request_code: str = "PR-1",
) -> PurchaseRequest:
    pr = PurchaseRequest(
        request_author="alice",
        request_name="Test request",
        request_code=request_code,
        supplier_name="Acme Ltd",
        supplier_email="supplier@example.com",
        request_details="Test purchase request",
        request_approval_status="approved",
    )
    db_session.add(pr)
    db_session.commit()
    db_session.refresh(pr)
    return pr


def authenticate() -> None:
    def override_current_user() -> User:
        return User(
            id=1,
            username="alice",
            role="employee",
        )

    app.dependency_overrides[current_user] = override_current_user


def use_invoice_client(invoice_client: Mock) -> None:
    app.dependency_overrides[get_invoice_client] = lambda: invoice_client


def test_invoices_endpoint_requires_user_authentication(
    client: TestClient,
) -> None:
    response = client.get("/purchase-request/PR-1/invoices")

    assert response.status_code == 401


def test_missing_purchase_request_does_not_call_invoice_app(
    client: TestClient,
) -> None:
    authenticate()
    invoice_client = Mock(spec=InvoiceClient)
    use_invoice_client(invoice_client)

    response = client.get("/purchase-request/PR-404/invoices")

    assert response.status_code == 404
    assert response.json() == {
        "detail": "Purchase request not found",
    }
    invoice_client.get_invoices.assert_not_called()


def test_returns_related_invoices(
    client: TestClient,
    db_session: Session,
) -> None:
    create_purchase_request(db_session)
    authenticate()

    invoice_client = Mock(spec=InvoiceClient)
    invoice_client.get_invoices.return_value = [
        InvoiceSummary(
            id=12,
            invoice_number="INV-2026-0512",
            invoice_sum=Decimal("1000.00"),
            invoice_sum_paid=Decimal("250.00"),
            invoice_status="prepaid",
        )
    ]
    use_invoice_client(invoice_client)

    response = client.get("/purchase-request/PR-1/invoices")

    assert response.status_code == 200
    assert response.json() == [
        {
            "id": 12,
            "invoice_number": "INV-2026-0512",
            "invoice_sum": 1000.0,
            "invoice_sum_paid": 250.0,
            "invoice_status": "prepaid",
        }
    ]
    invoice_client.get_invoices.assert_called_once_with("PR-1")
    assert "integration-key" not in response.text.lower()


def test_returns_empty_list_when_pr_has_no_invoices(
    client: TestClient,
    db_session: Session,
) -> None:
    create_purchase_request(db_session)
    authenticate()

    invoice_client = Mock(spec=InvoiceClient)
    invoice_client.get_invoices.return_value = []
    use_invoice_client(invoice_client)

    response = client.get("/purchase-request/PR-1/invoices")

    assert response.status_code == 200
    assert response.json() == []
    invoice_client.get_invoices.assert_called_once_with("PR-1")


@pytest.mark.parametrize(
    ("error", "expected_status"),
    [
        (InvoiceUnavailableError(), 503),
        (InvoiceIntegrationNotConfiguredError(), 503),
        (InvoiceInvalidResponseError(), 502),
    ],
)
def test_maps_invoice_client_errors(
    client: TestClient,
    db_session: Session,
    error: Exception,
    expected_status: int,
) -> None:
    create_purchase_request(db_session)
    authenticate()

    invoice_client = Mock(spec=InvoiceClient)
    invoice_client.get_invoices.side_effect = error
    use_invoice_client(invoice_client)

    response = client.get("/purchase-request/PR-1/invoices")

    assert response.status_code == expected_status
    assert response.json() == {"detail": str(error)}


def test_purchase_request_list_does_not_depend_on_invoice_app(
    client: TestClient,
    db_session: Session,
) -> None:
    create_purchase_request(db_session)
    authenticate()

    invoice_client = Mock(spec=InvoiceClient)
    invoice_client.get_invoices.side_effect = InvoiceUnavailableError()
    use_invoice_client(invoice_client)

    response = client.get("/purchase-request")

    assert response.status_code == 200
    assert len(response.json()) == 1
    invoice_client.get_invoices.assert_not_called()
