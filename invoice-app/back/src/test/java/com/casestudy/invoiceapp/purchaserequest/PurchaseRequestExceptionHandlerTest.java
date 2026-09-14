package com.casestudy.invoiceapp.purchaserequest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseRequestExceptionHandlerTest {

    private final PurchaseRequestExceptionHandler handler = new PurchaseRequestExceptionHandler();

    @ParameterizedTest
    @MethodSource("errorMappings")
    void mapsIntegrationErrorToSafeHttpResponse(PurchaseRequestIntegrationException exception,
                                                HttpStatus expectedStatus) {
        ResponseEntity<Map<String, String>> response = handler.handle(exception);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).containsExactly(Map.entry("message", exception.getMessage()));
    }

    private static Stream<Arguments> errorMappings() {
        return Stream.of(
                Arguments.of(PurchaseRequestIntegrationException.required(), HttpStatus.BAD_REQUEST),
                Arguments.of(PurchaseRequestIntegrationException.notFound(), HttpStatus.NOT_FOUND),
                Arguments.of(PurchaseRequestIntegrationException.notApproved(), HttpStatus.CONFLICT),
                Arguments.of(PurchaseRequestIntegrationException.unavailable(), HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(PurchaseRequestIntegrationException.notConfigured(), HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(PurchaseRequestIntegrationException.integrationError(), HttpStatus.BAD_GATEWAY)
        );
    }
}
