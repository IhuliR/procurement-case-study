package com.casestudy.invoiceapp.purchaserequest;

public class PurchaseRequestIntegrationException extends RuntimeException {

    public enum Type {
        NOT_FOUND,
        NOT_APPROVED,
        UNAVAILABLE,
        INTEGRATION_ERROR,
        NOT_CONFIGURED
    }

    private final Type type;

    private PurchaseRequestIntegrationException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    static PurchaseRequestIntegrationException notFound() {
        return new PurchaseRequestIntegrationException(Type.NOT_FOUND, "Purchase request not found");
    }

    static PurchaseRequestIntegrationException notApproved() {
        return new PurchaseRequestIntegrationException(Type.NOT_APPROVED, "Purchase request is not approved");
    }

    static PurchaseRequestIntegrationException unavailable() {
        return new PurchaseRequestIntegrationException(
                Type.UNAVAILABLE,
                "Purchase request service is unavailable"
        );
    }

    static PurchaseRequestIntegrationException integrationError() {
        return new PurchaseRequestIntegrationException(
                Type.INTEGRATION_ERROR,
                "Purchase request integration failed"
        );
    }

    static PurchaseRequestIntegrationException notConfigured() {
        return new PurchaseRequestIntegrationException(
                Type.NOT_CONFIGURED,
                "Purchase request integration is not configured"
        );
    }
}
