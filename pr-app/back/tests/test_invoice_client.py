"""Tests for the Invoice App HTTP integration client."""
from decimal import Decimal

import httpx
import pytest

from app.integrations.invoice import (
    InvoiceClient,
    InvoiceClientConfig,
    InvoiceIntegrationNotConfiguredError,
    InvoiceInvalidResponseError,
    InvoiceUnavailableError,
)


def make_config(
        integration_key: str | None = "test-integration-key",
) -> InvoiceClientConfig:
    return InvoiceClientConfig(
        base_url="http://invoice.test",
        integration_key=integration_key,
    )


def test_get_invoices_returns_validated_items() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/integration/invoices"
        assert request.url.params["purchase_request_number"] == "PR-1"
        assert request.headers["X-Integration-Key"] == "test-integration-key"

        return httpx.Response(
            200,
            json=[
                {
                    "id": 12,
                    "invoice_number": "INV-2026-0512",
                    "invoice_sum": 1000.00,
                    "invoice_sum_paid": 250.00,
                    "invoice_status": "prepaid",
                }
            ],
        )

    with httpx.Client(
        base_url="http://invoice.test",
        transport=httpx.MockTransport(handler),
    ) as http_client:
        client = InvoiceClient(make_config(), client=http_client)
        result = client.get_invoices("PR-1")

    assert len(result) == 1
    assert result[0].id == 12
    assert result[0].invoice_number == "INV-2026-0512"
    assert result[0].invoice_sum == Decimal("1000.0")
    assert result[0].invoice_sum_paid == Decimal("250.0")
    assert result[0].invoice_status == "prepaid"


def test_get_invoices_returns_empty_list() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=[])

    with httpx.Client(
        base_url="http://invoice.test",
        transport=httpx.MockTransport(handler),
    ) as http_client:
        client = InvoiceClient(make_config(), client=http_client)
        result = client.get_invoices("PR-1")

    assert result == []


@pytest.mark.parametrize("status_code", [502, 503, 504])
def test_retryable_status_is_retried_once(status_code: int) -> None:
    attempts = 0

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1

        if attempts == 1:
            return httpx.Response(status_code)

        return httpx.Response(200, json=[])

    with httpx.Client(
        base_url="http://invoice.test",
        transport=httpx.MockTransport(handler),
    ) as http_client:
        client = InvoiceClient(make_config(), client=http_client)
        result = client.get_invoices("PR-1")

    assert result == []
    assert attempts == 2


def test_two_retryable_failures_raise_unavailable() -> None:
    attempts = 0

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        return httpx.Response(503)

    with httpx.Client(
        base_url="http://invoice.test",
        transport=httpx.MockTransport(handler),
    ) as http_client:
        client = InvoiceClient(make_config(), client=http_client)

        with pytest.raises(InvoiceUnavailableError):
            client.get_invoices("PR-1")

    assert attempts == 2

@pytest.mark.parametrize(
        "status_code",
        [400, 401, 403, 404, 409, 500]
)
def test_non_retryable_status_is_not_retried(
    status_code: int,
) -> None:
    attempts = 0

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        return httpx.Response(status_code)

    with httpx.Client(
        base_url="http://invoice.test",
        transport=httpx.MockTransport(handler),
    ) as http_client:
        client = InvoiceClient(make_config(), client=http_client)

        with pytest.raises(InvoiceInvalidResponseError):
            client.get_invoices("PR-1")

    assert attempts == 1


def test_missing_key_fails_without_request() -> None:
    attempts = 0

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        return httpx.Response(200, json=[])

    with httpx.Client(
        base_url="http://invoice.test",
        transport=httpx.MockTransport(handler),
    ) as http_client:
        client = InvoiceClient(
            make_config(integration_key=None),
            client=http_client,
        )

        with pytest.raises(InvoiceIntegrationNotConfiguredError):
            client.get_invoices("PR-1")

    assert attempts == 0


def test_timeout_is_retried_once() -> None:
    attempts = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1

        if attempts == 1:
            raise httpx.ReadTimeout("Timed out", request=request)

        return httpx.Response(200, json=[])

    with httpx.Client(
        base_url="http://invoice.test",
        transport=httpx.MockTransport(handler),
    ) as http_client:
        client = InvoiceClient(make_config(), client=http_client)
        result = client.get_invoices("PR-1")

    assert result == []
    assert attempts == 2


def test_malformed_json_is_not_retried() -> None:
    attempts = 0

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        return httpx.Response(
            200,
            content=b"not-json",
            headers={"Content-Type": "application/json"},
        )

    with httpx.Client(
        base_url="http://invoice.test",
        transport=httpx.MockTransport(handler),
    ) as http_client:
        client = InvoiceClient(make_config(), client=http_client)

        with pytest.raises(InvoiceInvalidResponseError):
            client.get_invoices("PR-1")

    assert attempts == 1


@pytest.mark.parametrize(
    "payload",
    [
        {},
        [{"id": 1}],
        [{
            "id": 1,
            "invoice_number": "",
            "invoice_sum": 100,
            "invoice_sum_paid": 0,
            "invoice_status": "new",
        }],
        [{
            "id": 1,
            "invoice_number": "INV-1",
            "invoice_sum": -1,
            "invoice_sum_paid": 0,
            "invoice_status": "new",
        }],
        [{
            "id": 1,
            "invoice_number": "INV-1",
            "invoice_sum": 100,
            "invoice_sum_paid": 0,
            "invoice_status": "   ",
        }],
    ],
)
def test_invalid_contract_is_not_retried(payload: object) -> None:
    attempts = 0

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        return httpx.Response(200, json=payload)

    with httpx.Client(
        base_url="http://invoice.test",
        transport=httpx.MockTransport(handler),
    ) as http_client:
        client = InvoiceClient(make_config(), client=http_client)

        with pytest.raises(InvoiceInvalidResponseError):
            client.get_invoices("PR-1")

    assert attempts == 1
