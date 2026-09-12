package com.casestudy.invoiceapp.purchaserequest;

import com.casestudy.invoiceapp.invoice.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.casestudy.invoiceapp.purchaserequest.PurchaseRequestIntegrationException.Type.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseRequestRelationshipServiceTest {

    @Mock
    private PurchaseRequestClient purchaseRequestClient;

    private PurchaseRequestRelationshipService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseRequestRelationshipService(purchaseRequestClient);
    }

    @Test
    void createWithNullCodeIsRequiredWithoutClientCall() {
        assertRequired(() -> service.validateForCreate(null));
        verifyNoInteractions(purchaseRequestClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void createWithBlankCodeIsRequiredWithoutClientCall(String submittedCode) {
        assertRequired(() -> service.validateForCreate(submittedCode));
        verifyNoInteractions(purchaseRequestClient);
    }

    @Test
    void successfulCreateReturnsValidatedRelationshipAndTimestamp() {
        when(purchaseRequestClient.getInvoiceContext("PR-1"))
                .thenReturn(new PurchaseRequestInvoiceContext("PR-1", "Acme Ltd"));
        Instant before = Instant.now();

        PurchaseRequestRelationshipService.ValidatedRelationship result =
                service.validateForCreate("  PR-1  ");

        Instant after = Instant.now();
        assertThat(result.requestCode()).isEqualTo("PR-1");
        assertThat(result.supplierName()).isEqualTo("Acme Ltd");
        assertThat(result.validatedAt()).isNotNull().isBetween(before, after);
        verify(purchaseRequestClient).getInvoiceContext("PR-1");
    }

    @Test
    void createPreservesClientException() {
        PurchaseRequestIntegrationException clientException =
                PurchaseRequestIntegrationException.notFound();
        when(purchaseRequestClient.getInvoiceContext("PR-1")).thenThrow(clientException);

        assertThatThrownBy(() -> service.validateForCreate("PR-1"))
                .isSameAs(clientException);
    }

    @Test
    void updateWithNullCodeDoesNothing() {
        Instant validatedAt = Instant.parse("2026-01-01T00:00:00Z");
        Invoice invoice = invoice("PR-1", "Original Supplier", validatedAt);

        Optional<PurchaseRequestRelationshipService.ValidatedRelationship> result =
                service.validateForUpdate(invoice, null);

        assertThat(result).isEmpty();
        assertInvoiceUnchanged(invoice, "PR-1", "Original Supplier", validatedAt);
        verifyNoInteractions(purchaseRequestClient);
    }

    @Test
    void updateWithSameValidatedCodeDoesNotRevalidate() {
        Instant validatedAt = Instant.parse("2026-01-01T00:00:00Z");
        Invoice invoice = invoice("PR-1", "Original Supplier", validatedAt);

        Optional<PurchaseRequestRelationshipService.ValidatedRelationship> result =
                service.validateForUpdate(invoice, "  PR-1  ");

        assertThat(result).isEmpty();
        assertInvoiceUnchanged(invoice, "PR-1", "Original Supplier", validatedAt);
        verifyNoInteractions(purchaseRequestClient);
    }

    @Test
    void updateWithNewCodeReturnsValidatedRelationshipWithoutMutation() {
        Instant validatedAt = Instant.parse("2026-01-01T00:00:00Z");
        Invoice invoice = invoice("PR-1", "Original Supplier", validatedAt);
        when(purchaseRequestClient.getInvoiceContext("PR-2"))
                .thenReturn(new PurchaseRequestInvoiceContext("PR-2", "New Supplier"));

        Optional<PurchaseRequestRelationshipService.ValidatedRelationship> result =
                service.validateForUpdate(invoice, "  PR-2 ");

        assertThat(result).get()
                .extracting(
                        PurchaseRequestRelationshipService.ValidatedRelationship::requestCode,
                        PurchaseRequestRelationshipService.ValidatedRelationship::supplierName
                )
                .containsExactly("PR-2", "New Supplier");
        assertThat(result.orElseThrow().validatedAt()).isNotNull();
        assertInvoiceUnchanged(invoice, "PR-1", "Original Supplier", validatedAt);
        verify(purchaseRequestClient).getInvoiceContext("PR-2");
    }

    @Test
    void updateLegacyRelationshipRevalidatesSameCode() {
        Invoice invoice = invoice("PR-1", "Original Supplier", null);
        when(purchaseRequestClient.getInvoiceContext("PR-1"))
                .thenReturn(new PurchaseRequestInvoiceContext("PR-1", "Canonical Supplier"));

        Optional<PurchaseRequestRelationshipService.ValidatedRelationship> result =
                service.validateForUpdate(invoice, "PR-1");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().supplierName()).isEqualTo("Canonical Supplier");
        assertInvoiceUnchanged(invoice, "PR-1", "Original Supplier", null);
        verify(purchaseRequestClient).getInvoiceContext("PR-1");
    }

    @Test
    void updateInvoiceWithoutPurchaseRequestValidatesSubmittedCode() {
        Invoice invoice = invoice(null, "Original Supplier", null);
        when(purchaseRequestClient.getInvoiceContext("PR-1"))
                .thenReturn(new PurchaseRequestInvoiceContext("PR-1", "Canonical Supplier"));

        Optional<PurchaseRequestRelationshipService.ValidatedRelationship> result =
                service.validateForUpdate(invoice, " PR-1 ");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().requestCode()).isEqualTo("PR-1");
        assertInvoiceUnchanged(invoice, null, "Original Supplier", null);
        verify(purchaseRequestClient).getInvoiceContext("PR-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void updateWithBlankCodeIsRequiredWithoutMutationOrClientCall(String submittedCode) {
        Instant validatedAt = Instant.parse("2026-01-01T00:00:00Z");
        Invoice invoice = invoice("PR-1", "Original Supplier", validatedAt);

        assertRequired(() -> service.validateForUpdate(invoice, submittedCode));

        assertInvoiceUnchanged(invoice, "PR-1", "Original Supplier", validatedAt);
        verifyNoInteractions(purchaseRequestClient);
    }

    @Test
    void updatePreservesClientExceptionWithoutMutation() {
        Instant validatedAt = Instant.parse("2026-01-01T00:00:00Z");
        Invoice invoice = invoice("PR-1", "Original Supplier", validatedAt);
        PurchaseRequestIntegrationException clientException =
                PurchaseRequestIntegrationException.notApproved();
        when(purchaseRequestClient.getInvoiceContext("PR-2")).thenThrow(clientException);

        assertThatThrownBy(() -> service.validateForUpdate(invoice, "PR-2"))
                .isSameAs(clientException);
        assertInvoiceUnchanged(invoice, "PR-1", "Original Supplier", validatedAt);
    }

    private static Invoice invoice(String requestCode, String supplier, Instant validatedAt) {
        Invoice invoice = new Invoice();
        invoice.setPurchaseRequestNumber(requestCode);
        invoice.setSupplier(supplier);
        invoice.setPurchaseRequestValidatedAt(validatedAt);
        return invoice;
    }

    private static void assertInvoiceUnchanged(Invoice invoice,
                                               String requestCode,
                                               String supplier,
                                               Instant validatedAt) {
        assertThat(invoice.getPurchaseRequestNumber()).isEqualTo(requestCode);
        assertThat(invoice.getSupplier()).isEqualTo(supplier);
        assertThat(invoice.getPurchaseRequestValidatedAt()).isEqualTo(validatedAt);
    }

    private static void assertRequired(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(PurchaseRequestIntegrationException.class, exception -> {
                    assertThat(exception.getType()).isEqualTo(REQUIRED);
                    assertThat(exception.getMessage()).isEqualTo("Purchase request is required");
                });
    }
}
