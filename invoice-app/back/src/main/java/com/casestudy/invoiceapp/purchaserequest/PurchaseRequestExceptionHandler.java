package com.casestudy.invoiceapp.purchaserequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PurchaseRequestExceptionHandler {

    @ExceptionHandler(PurchaseRequestIntegrationException.class)
    public ResponseEntity<Map<String, String>> handle(PurchaseRequestIntegrationException exception) {
        HttpStatus status = switch (exception.getType()) {
            case REQUIRED -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_APPROVED -> HttpStatus.CONFLICT;
            case UNAVAILABLE, NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTEGRATION_ERROR -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(Map.of("message", exception.getMessage()));
    }
}
