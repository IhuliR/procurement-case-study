package com.casestudy.invoiceapp.purchaserequest;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.function.Supplier;

public class PurchaseRequestClient {

    private static final String INTEGRATION_KEY_HEADER = "X-Integration-Key";
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;
    private final String integrationKey;

    private enum Endpoint {
        INVOICE_OPTIONS,
        INVOICE_CONTEXT
    }

    public PurchaseRequestClient(RestClient restClient, String integrationKey) {
        this.restClient = restClient;
        this.integrationKey = integrationKey;
    }

    public List<PurchaseRequestOption> getInvoiceOptions() {
        List<PurchaseRequestOption> options = execute(Endpoint.INVOICE_OPTIONS, () -> restClient.get()
                .uri("/integration/purchase-requests/invoice-options")
                .header(INTEGRATION_KEY_HEADER, integrationKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                }));

        if (options == null || options.stream().anyMatch(option -> option == null || !isValid(option))) {
            throw PurchaseRequestIntegrationException.integrationError();
        }
        return options;
    }

    public PurchaseRequestInvoiceContext getInvoiceContext(String requestCode) {
        if (!hasText(requestCode)) {
            throw PurchaseRequestIntegrationException.integrationError();
        }

        PurchaseRequestInvoiceContext context = execute(Endpoint.INVOICE_CONTEXT, () -> restClient.get()
                .uri("/integration/purchase-requests/{requestCode}/invoice-context", requestCode)
                .header(INTEGRATION_KEY_HEADER, integrationKey)
                .retrieve()
                .body(PurchaseRequestInvoiceContext.class));

        if (context == null || !isValid(context) || !requestCode.equals(context.requestCode())) {
            throw PurchaseRequestIntegrationException.integrationError();
        }
        return context;
    }

    private <T> T execute(Endpoint endpoint, Supplier<T> request) {
        if (integrationKey == null || integrationKey.isBlank()) {
            throw PurchaseRequestIntegrationException.notConfigured();
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return request.get();
            } catch (ResourceAccessException exception) {
                if (attempt == MAX_ATTEMPTS) {
                    throw PurchaseRequestIntegrationException.unavailable();
                }
            } catch (RestClientResponseException exception) {
                HttpStatusCode status = exception.getStatusCode();
                if (endpoint == Endpoint.INVOICE_CONTEXT && status.value() == 404) {
                    throw PurchaseRequestIntegrationException.notFound();
                }
                if (endpoint == Endpoint.INVOICE_CONTEXT && status.value() == 409) {
                    throw PurchaseRequestIntegrationException.notApproved();
                }
                if (isRetryable(status)) {
                    if (attempt == MAX_ATTEMPTS) {
                        throw PurchaseRequestIntegrationException.unavailable();
                    }
                } else {
                    throw PurchaseRequestIntegrationException.integrationError();
                }
            } catch (RestClientException exception) {
                throw PurchaseRequestIntegrationException.integrationError();
            }
        }

        throw PurchaseRequestIntegrationException.unavailable();
    }

    private static boolean isRetryable(HttpStatusCode status) {
        return status.value() == 502 || status.value() == 503 || status.value() == 504;
    }

    private static boolean isValid(PurchaseRequestOption option) {
        return hasText(option.requestCode())
                && hasText(option.requestName())
                && hasText(option.supplierName());
    }

    private static boolean isValid(PurchaseRequestInvoiceContext context) {
        return hasText(context.requestCode()) && hasText(context.supplierName());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
