package com.casestudy.invoiceapp.purchaserequest;

import com.casestudy.invoiceapp.invoice.Invoice;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class PurchaseRequestRelationshipService {

    private final PurchaseRequestClient purchaseRequestClient;

    public PurchaseRequestRelationshipService(PurchaseRequestClient purchaseRequestClient) {
        this.purchaseRequestClient = purchaseRequestClient;
    }

    public ValidatedRelationship validateForCreate(String submittedCode) {
        return validate(normalizeRequired(submittedCode));
    }

    public Optional<ValidatedRelationship> validateForUpdate(Invoice invoice, String submittedCode) {
        if (submittedCode == null) {
            return Optional.empty();
        }

        String normalizedCode = normalizeRequired(submittedCode);
        if (invoice.getPurchaseRequestValidatedAt() != null
                && normalizedCode.equals(invoice.getPurchaseRequestNumber())) {
            return Optional.empty();
        }

        return Optional.of(validate(normalizedCode));
    }

    private ValidatedRelationship validate(String requestCode) {
        PurchaseRequestInvoiceContext context = purchaseRequestClient.getInvoiceContext(requestCode);
        return new ValidatedRelationship(context.requestCode(), context.supplierName(), Instant.now());
    }

    private static String normalizeRequired(String submittedCode) {
        if (submittedCode == null || submittedCode.isBlank()) {
            throw PurchaseRequestIntegrationException.required();
        }
        return submittedCode.trim();
    }

    public record ValidatedRelationship(
            String requestCode,
            String supplierName,
            Instant validatedAt
    ) {
    }
}
