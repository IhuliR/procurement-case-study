"""Tests for backend-to-backend Purchase Request endpoints."""
import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import PurchaseRequest


OPTIONS_URL = "/integration/purchase-requests/invoice-options"


def test_integration_endpoint_requires_key(
    client: TestClient,
) -> None:
    response = client.get(OPTIONS_URL)

    assert response.status_code == 401
    assert response.json() == {
        "detail": "Invalid integration key",
    }


def test_integration_endpoint_rejects_wrong_key(
    client: TestClient,
) -> None:
    response = client.get(
        OPTIONS_URL,
        headers={"X-Integration-Key": "wrong-key"},
    )

    assert response.status_code == 401
    assert response.json() == {
        "detail": "Invalid integration key",
    }


def create_purchase_request(
    db_session: Session,
    *,
    request_code: str,
    approval_status: str,
) -> PurchaseRequest:
    pr = PurchaseRequest(
        request_author="alice",
        request_name=f"Request {request_code}",
        request_code=request_code,
        supplier_name="Acme Ltd",
        supplier_email="supplier@example.com",
        request_details="Test purchase request",
        request_approval_status=approval_status,
    )
    db_session.add(pr)
    db_session.commit()
    db_session.refresh(pr)
    return pr


def test_invoice_options_include_only_approved_requests(
    client: TestClient,
    db_session: Session,
    integration_headers: dict[str, str],
) -> None:
    create_purchase_request(
        db_session,
        request_code="PR-1",
        approval_status="initiated",
    )
    create_purchase_request(
        db_session,
        request_code="PR-2",
        approval_status="approved",
    )

    response = client.get(
        OPTIONS_URL,
        headers=integration_headers,
    )

    assert response.status_code == 200
    assert response.json() == [
        {
            "request_code": "PR-2",
            "request_name": "Request PR-2",
            "supplier_name": "Acme Ltd",
        }
    ]


def test_invoice_context_returns_approved_request(
    client: TestClient,
    db_session: Session,
    integration_headers: dict[str, str],
) -> None:
    create_purchase_request(
        db_session,
        request_code="PR-10",
        approval_status="approved",
    )

    response = client.get(
        "/integration/purchase-requests/PR-10/invoice-context",
        headers=integration_headers,
    )

    assert response.status_code == 200
    assert response.json() == {
        "request_code": "PR-10",
        "supplier_name": "Acme Ltd",
    }


def test_invoice_context_returns_404_for_missing_request(
    client: TestClient,
    integration_headers: dict[str, str],
) -> None:
    response = client.get(
        "/integration/purchase-requests/PR-404/invoice-context",
        headers=integration_headers,
    )

    assert response.status_code == 404


@pytest.mark.parametrize(
    "approval_status",
    [
        "initiated",
        "sent for approval",
        "rejected",
    ],
)
def test_invoice_context_returns_409_for_unapproved_request(
    approval_status: str,
    client: TestClient,
    db_session: Session,
    integration_headers: dict[str, str],
) -> None:
    create_purchase_request(
        db_session,
        request_code="PR-20",
        approval_status=approval_status,
    )

    response = client.get(
        "/integration/purchase-requests/PR-20/invoice-context",
        headers=integration_headers,
    )

    assert response.status_code == 409


def test_integration_endpoint_rejects_request_when_key_is_not_configured(
    client: TestClient,
    integration_headers: dict[str, str],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv(
        "PR_INTEGRATION_API_KEY",
        raising=False,
    )

    response = client.get(
        OPTIONS_URL,
        headers=integration_headers,
    )

    assert response.status_code == 401
    assert response.json() == {
        "detail": "Invalid integration key",
    }
