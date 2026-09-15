"""HTTP client for the Invoice App integration."""
from __future__ import annotations

import os
from dataclasses import dataclass

import httpx
from pydantic import TypeAdapter, ValidationError

from ..schemas import InvoiceSummary


_INVOICE_LIST_ADAPTER = TypeAdapter(list[InvoiceSummary])
_RETRYABLE_STATUSES = {502, 503, 504}
_MAX_ATTEMPTS = 2


class InvoiceIntegrationError(RuntimeError):
    """Base error for controlled Invoice App integration failures."""


class InvoiceUnavailableError(InvoiceIntegrationError):
    def __init__(self) -> None:
        super().__init__("Invoice App is temporarily unavailable.")


class InvoiceInvalidResponseError(InvoiceIntegrationError):
    def __init__(self) -> None:
        super().__init__("Invoice App returned an invalid response.")


class InvoiceIntegrationNotConfiguredError(InvoiceIntegrationError):
    def __init__(self) -> None:
        super().__init__("Invoice integration is not configured.")


@dataclass(frozen=True)
class InvoiceClientConfig:
    base_url: str | None
    integration_key: str | None
    connect_timeout: float = 1.0
    read_timeout: float = 2.0

    @classmethod
    def from_environment(cls) -> InvoiceClientConfig:
        return cls(
            base_url=os.environ.get("INVOICE_APP_URL"),
            integration_key=os.environ.get("INVOICE_INTEGRATION_API_KEY"),
        )


class InvoiceClient:
    def __init__(
            self,
            config: InvoiceClientConfig,
            client: httpx.Client | None = None,
    ) -> None:
        self._config = config
        self._client = client

    def get_invoices(self, request_code: str) -> list[InvoiceSummary]:
        base_url, integration_key = self._validated_config()

        if self._client is not None:
            return self._request_with_retry(
                client=self._client,
                request_code=request_code,
                integration_key=integration_key,
            )

        timeout = httpx.Timeout(
            self._config.read_timeout,
            connect=self._config.connect_timeout,
        )
        with httpx.Client(base_url=base_url, timeout=timeout) as client:
            return self._request_with_retry(
                client=client,
                request_code=request_code,
                integration_key=integration_key,
            )

    def _validated_config(self) -> tuple[str, str]:
        base_url = (self._config.base_url or "").strip()
        integration_key = (self._config.integration_key or "").strip()

        if not base_url or not integration_key:
            raise InvoiceIntegrationNotConfiguredError()

        return base_url.rstrip("/"), integration_key

    @classmethod
    def _request_with_retry(
        cls,
        client: httpx.Client,
        request_code: str,
        integration_key: str,
    ) -> list[InvoiceSummary]:
        for attempt in range(_MAX_ATTEMPTS):
            try:
                return cls._request(
                    client=client,
                    request_code=request_code,
                    integration_key=integration_key,
                )
            except InvoiceUnavailableError:
                if attempt == _MAX_ATTEMPTS - 1:
                    raise
        raise RuntimeError("Unreachable")

    @staticmethod
    def _request(
        client: httpx.Client,
        request_code: str,
        integration_key: str,
    ) -> list[InvoiceSummary]:
        try:
            response = client.get(
                "/integration/invoices",
                params={"purchase_request_number": request_code},
                headers={"X-Integration-Key": integration_key},
            )
            response.raise_for_status()
        except httpx.RequestError:
            raise InvoiceUnavailableError() from None
        except httpx.HTTPStatusError as error:
            if error.response.status_code in _RETRYABLE_STATUSES:
                raise InvoiceUnavailableError() from None
            raise InvoiceInvalidResponseError() from None

        try:
            return _INVOICE_LIST_ADAPTER.validate_json(response.content)
        except ValidationError:
            raise InvoiceInvalidResponseError() from None


def get_invoice_client() -> InvoiceClient:
    return InvoiceClient(InvoiceClientConfig.from_environment())
