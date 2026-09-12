package com.casestudy.invoiceapp.purchaserequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import static com.casestudy.invoiceapp.purchaserequest.PurchaseRequestIntegrationException.Type.INTEGRATION_ERROR;
import static com.casestudy.invoiceapp.purchaserequest.PurchaseRequestIntegrationException.Type.NOT_APPROVED;
import static com.casestudy.invoiceapp.purchaserequest.PurchaseRequestIntegrationException.Type.NOT_CONFIGURED;
import static com.casestudy.invoiceapp.purchaserequest.PurchaseRequestIntegrationException.Type.NOT_FOUND;
import static com.casestudy.invoiceapp.purchaserequest.PurchaseRequestIntegrationException.Type.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PurchaseRequestClientTest {

    private static final String BASE_URL = "http://purchase-request.test";
    private static final String INTEGRATION_KEY = "test-integration-key";
    private static final String OPTIONS_URL = BASE_URL + "/integration/purchase-requests/invoice-options";
    private static final String CONTEXT_URL =
            BASE_URL + "/integration/purchase-requests/PR-1/invoice-context";

    private MockRestServiceServer server;
    private PurchaseRequestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PurchaseRequestClient(builder.build(), INTEGRATION_KEY);
    }

    @Test
    void deserializesInvoiceOptions() {
        server.expect(requestTo(OPTIONS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{
                          "request_code": "PR-1",
                          "request_name": "First test request",
                          "supplier_name": "Acme Ltd"
                        }]
                        """, MediaType.APPLICATION_JSON));

        List<PurchaseRequestOption> result = client.getInvoiceOptions();

        assertThat(result).containsExactly(
                new PurchaseRequestOption("PR-1", "First test request", "Acme Ltd")
        );
        server.verify();
    }

    @Test
    void deserializesInvoiceContext() {
        server.expect(requestTo(CONTEXT_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"request_code": "PR-1", "supplier_name": "Acme Ltd"}
                        """, MediaType.APPLICATION_JSON));

        PurchaseRequestInvoiceContext result = client.getInvoiceContext("PR-1");

        assertThat(result).isEqualTo(new PurchaseRequestInvoiceContext("PR-1", "Acme Ltd"));
        server.verify();
    }

    @Test
    void sendsIntegrationKeyHeader() {
        server.expect(requestTo(OPTIONS_URL))
                .andExpect(header("X-Integration-Key", INTEGRATION_KEY))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.getInvoiceOptions();

        server.verify();
    }

    @Test
    void emptyIntegrationKeyFailsClosedWithoutHttpCall() {
        PurchaseRequestClient clientWithoutKey = new PurchaseRequestClient(
                RestClient.builder().baseUrl(BASE_URL).build(),
                "  "
        );

        assertError(NOT_CONFIGURED, clientWithoutKey::getInvoiceOptions);
        server.verify();
    }

    @Test
    void notFoundIsDistinctAndIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(CONTEXT_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertError(NOT_FOUND, () -> client.getInvoiceContext("PR-1"));
        server.verify();
    }

    @Test
    void notApprovedIsDistinctAndIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(CONTEXT_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT));

        assertError(NOT_APPROVED, () -> client.getInvoiceContext("PR-1"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 409})
    void invoiceOptionsBusinessStatusIsAnIntegrationErrorWithoutRetry(int status) {
        server.expect(ExpectedCount.once(), requestTo(OPTIONS_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatusCode.valueOf(status)));

        assertError(INTEGRATION_ERROR, client::getInvoiceOptions);
        server.verify();
    }

    @Test
    void timeoutIsRetriedOnceThenMappedToUnavailable() {
        server.expect(ExpectedCount.twice(), requestTo(OPTIONS_URL))
                .andRespond(withException(new SocketTimeoutException("upstream details")));

        assertError(UNAVAILABLE, client::getInvoiceOptions);
        server.verify();
    }

    @Test
    void networkErrorIsRetriedOnceThenMappedToUnavailable() {
        server.expect(ExpectedCount.twice(), requestTo(OPTIONS_URL))
                .andRespond(withException(new ConnectException("upstream details")));

        assertError(UNAVAILABLE, client::getInvoiceOptions);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504})
    void retryableUpstreamStatusIsRetriedOnceThenMappedToUnavailable(int status) {
        server.expect(ExpectedCount.twice(), requestTo(OPTIONS_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatusCode.valueOf(status)));

        assertError(UNAVAILABLE, client::getInvoiceOptions);
        server.verify();
    }

    @Test
    void retryableStatusThenSuccessReturnsSecondAttemptResult() {
        server.expect(ExpectedCount.once(), requestTo(OPTIONS_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(ExpectedCount.once(), requestTo(OPTIONS_URL))
                .andRespond(withSuccess("""
                        [{
                          "request_code": "PR-1",
                          "request_name": "First test request",
                          "supplier_name": "Acme Ltd"
                        }]
                        """, MediaType.APPLICATION_JSON));

        List<PurchaseRequestOption> result = client.getInvoiceOptions();

        assertThat(result).containsExactly(
                new PurchaseRequestOption("PR-1", "First test request", "Acme Ltd")
        );
        server.verify();
    }

    @Test
    void otherClientErrorIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(OPTIONS_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        assertError(INTEGRATION_ERROR, client::getInvoiceOptions);
        server.verify();
    }

    @Test
    void internalServerErrorIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(OPTIONS_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        assertError(INTEGRATION_ERROR, client::getInvoiceOptions);
        server.verify();
    }

    @Test
    void malformedJsonIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(OPTIONS_URL))
                .andRespond(withSuccess("[{", MediaType.APPLICATION_JSON));

        assertError(INTEGRATION_ERROR, client::getInvoiceOptions);
        server.verify();
    }

    @Test
    void missingRequiredResponseFieldIsRejected() {
        server.expect(ExpectedCount.once(), requestTo(OPTIONS_URL))
                .andRespond(withSuccess("""
                        [{"request_code": "PR-1", "supplier_name": "Acme Ltd"}]
                        """, MediaType.APPLICATION_JSON));

        assertError(INTEGRATION_ERROR, client::getInvoiceOptions);
        server.verify();
    }

    @Test
    void blankRequiredResponseFieldIsRejected() {
        server.expect(ExpectedCount.once(), requestTo(CONTEXT_URL))
                .andRespond(withSuccess("""
                        {"request_code": "PR-1", "supplier_name": "  "}
                        """, MediaType.APPLICATION_JSON));

        assertError(INTEGRATION_ERROR, () -> client.getInvoiceContext("PR-1"));
        server.verify();
    }

    @Test
    void mismatchedRequestCodeIsRejected() {
        server.expect(ExpectedCount.once(), requestTo(CONTEXT_URL))
                .andRespond(withSuccess("""
                        {"request_code": "PR-2", "supplier_name": "Acme Ltd"}
                        """, MediaType.APPLICATION_JSON));

        assertError(INTEGRATION_ERROR, () -> client.getInvoiceContext("PR-1"));
        server.verify();
    }

    private static void assertError(PurchaseRequestIntegrationException.Type expectedType,
                                    Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(PurchaseRequestIntegrationException.class,
                        exception -> assertThat(exception.getType()).isEqualTo(expectedType))
                .hasNoCause();
    }
}
