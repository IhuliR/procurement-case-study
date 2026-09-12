package com.casestudy.invoiceapp.purchaserequest;

import com.casestudy.invoiceapp.auth.CurrentUserFilter;
import com.casestudy.invoiceapp.invoice.Invoice;
import com.casestudy.invoiceapp.invoice.InvoiceController;
import com.casestudy.invoiceapp.invoice.InvoiceRepository;
import com.casestudy.invoiceapp.invoice.dto.InvoiceSummaryDto;
import com.casestudy.invoiceapp.invoice.dto.InvoiceUpdateDto;
import com.casestudy.invoiceapp.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InvoiceControllerPurchaseRequestTest {

    private static final Instant ORIGINAL_VALIDATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant NEW_VALIDATED_AT = Instant.parse("2026-02-01T00:00:00Z");

    @Mock
    private InvoiceRepository invoices;

    @Mock
    private PurchaseRequestRelationshipService relationships;

    @Mock
    private PurchaseRequestClient purchaseRequestClient;

    private InvoiceController controller;

    @BeforeEach
    void setUp() {
        controller = new InvoiceController(invoices, relationships, purchaseRequestClient);
    }

    @Test
    void createSavesValidatedRelationshipAndIgnoresSubmittedSupplier() throws Exception {
        PurchaseRequestRelationshipService.ValidatedRelationship relationship =
                new PurchaseRequestRelationshipService.ValidatedRelationship(
                        "PR-1", "Verified Supplier", NEW_VALIDATED_AT
                );
        when(relationships.validateForCreate("  PR-1  ")).thenReturn(relationship);
        when(invoices.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.create(
                financeCreateRequest(), "INV-1", "Untrusted Supplier", "  PR-1  ",
                new BigDecimal("100.00"), null, "created", null
        );

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        InOrder order = inOrder(relationships, invoices);
        order.verify(relationships).validateForCreate("  PR-1  ");
        order.verify(invoices).save(invoiceCaptor.capture());
        Invoice saved = invoiceCaptor.getValue();
        assertThat(saved.getPurchaseRequestNumber()).isEqualTo("PR-1");
        assertThat(saved.getSupplier()).isEqualTo("Verified Supplier");
        assertThat(saved.getPurchaseRequestValidatedAt()).isEqualTo(NEW_VALIDATED_AT);
        assertThat(saved.getInvoiceNumber()).isEqualTo("INV-1");
        assertThat(saved.getInvoiceSum()).isEqualByComparingTo("100.00");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void missingOrBlankPurchaseRequestDoesNotSave(String submittedCode) {
        when(relationships.validateForCreate(submittedCode))
                .thenThrow(PurchaseRequestIntegrationException.required());

        assertThatThrownBy(() -> controller.create(
                financeRequest(), "INV-1", "Supplier", submittedCode,
                BigDecimal.TEN, null, "created", null
        )).isInstanceOfSatisfying(PurchaseRequestIntegrationException.class,
                exception -> assertThat(exception.getType())
                        .isEqualTo(PurchaseRequestIntegrationException.Type.REQUIRED));

        verify(invoices, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("createValidationFailures")
    void createValidationFailureDoesNotSave(PurchaseRequestIntegrationException exception) {
        when(relationships.validateForCreate("PR-1")).thenThrow(exception);

        assertThatThrownBy(() -> controller.create(
                financeRequest(), "INV-1", "Supplier", "PR-1",
                BigDecimal.TEN, null, "created", null
        )).isSameAs(exception);

        verify(invoices, never()).save(any());
    }

    @Test
    void updateWithNewPurchaseRequestReplacesWholeRelationship() {
        Invoice invoice = existingInvoice();
        InvoiceUpdateDto body = new InvoiceUpdateDto();
        body.purchaseRequestNumber = "PR-2";
        body.supplier = "Untrusted Supplier";
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        when(relationships.validateForUpdate(invoice, "PR-2"))
                .thenReturn(Optional.of(new PurchaseRequestRelationshipService.ValidatedRelationship(
                        "PR-2", "Verified New Supplier", NEW_VALIDATED_AT
                )));

        InvoiceSummaryDto result = controller.update(financeRequest(), 1L, body);

        assertThat(result.purchaseRequestNumber).isEqualTo("PR-2");
        assertThat(result.supplier).isEqualTo("Verified New Supplier");
        assertThat(invoice.getPurchaseRequestValidatedAt()).isEqualTo(NEW_VALIDATED_AT);
        verify(invoices).save(invoice);
    }

    @Test
    void updateWithNullPurchaseRequestPreservesRelationshipAndSupplier() {
        Invoice invoice = existingInvoice();
        InvoiceUpdateDto body = new InvoiceUpdateDto();
        body.supplier = "Untrusted Supplier";
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        when(relationships.validateForUpdate(invoice, null)).thenReturn(Optional.empty());

        controller.update(financeRequest(), 1L, body);

        assertOriginalRelationship(invoice);
        verify(relationships).validateForUpdate(invoice, null);
        verify(invoices).save(invoice);
    }

    @Test
    void updateWithUnchangedValidatedPurchaseRequestPreservesRelationship() {
        Invoice invoice = existingInvoice();
        InvoiceUpdateDto body = new InvoiceUpdateDto();
        body.purchaseRequestNumber = "PR-1";
        body.supplier = "Untrusted Supplier";
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        when(relationships.validateForUpdate(invoice, "PR-1")).thenReturn(Optional.empty());

        controller.update(financeRequest(), 1L, body);

        assertOriginalRelationship(invoice);
        verify(relationships).validateForUpdate(invoice, "PR-1");
        verify(invoices).save(invoice);
    }

    @Test
    void updateValidationFailureDoesNotMutateOrSaveInvoice() {
        Invoice invoice = existingInvoice();
        InvoiceUpdateDto body = new InvoiceUpdateDto();
        body.invoiceNumber = "CHANGED";
        body.purchaseRequestNumber = "PR-2";
        body.supplier = "Untrusted Supplier";
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        PurchaseRequestIntegrationException exception =
                PurchaseRequestIntegrationException.notApproved();
        when(relationships.validateForUpdate(invoice, "PR-2")).thenThrow(exception);

        assertThatThrownBy(() -> controller.update(financeRequest(), 1L, body))
                .isSameAs(exception);

        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-1");
        assertOriginalRelationship(invoice);
        verify(invoices, never()).save(any());
    }

    @Test
    void purchaseRequestOptionsReturnsClientResponseWithoutIntegrationKey() throws Exception {
        when(purchaseRequestClient.getInvoiceOptions()).thenReturn(List.of(
                new PurchaseRequestOption("PR-1", "First request", "Acme Ltd")
        ));

        mockMvc().perform(get("/invoice/purchase-request-options")
                        .requestAttr(CurrentUserFilter.CURRENT_USER_ATTR, financeUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].request_code").value("PR-1"))
                .andExpect(jsonPath("$[0].request_name").value("First request"))
                .andExpect(jsonPath("$[0].supplier_name").value("Acme Ltd"))
                .andExpect(jsonPath("$[0].integration_key").doesNotExist())
                .andExpect(content().string(not(containsString("X-Integration-Key"))));
    }

    @Test
    void purchaseRequestOptionsRequiresInvoiceUserAuthentication() throws Exception {
        mockMvc().perform(get("/invoice/purchase-request-options"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(purchaseRequestClient);
    }

    @Test
    void purchaseRequestOptionsMapsUnavailableService() throws Exception {
        when(purchaseRequestClient.getInvoiceOptions())
                .thenThrow(PurchaseRequestIntegrationException.unavailable());

        mockMvc().perform(get("/invoice/purchase-request-options")
                        .requestAttr(CurrentUserFilter.CURRENT_USER_ATTR, financeUser()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"message":"Purchase request service is unavailable"}
                        """));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new PurchaseRequestExceptionHandler())
                .build();
    }

    private static HttpServletRequest financeRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentUserFilter.CURRENT_USER_ATTR, financeUser());
        return request;
    }

    private static HttpServletRequest financeCreateRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = financeUser();
        when(user.getUsername()).thenReturn("finadmin");
        request.setAttribute(CurrentUserFilter.CURRENT_USER_ATTR, user);
        return request;
    }

    private static User financeUser() {
        User user = mock(User.class);
        when(user.getRole()).thenReturn("finance");
        return user;
    }

    private static Invoice existingInvoice() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-1");
        invoice.setSupplier("Original Supplier");
        invoice.setPurchaseRequestNumber("PR-1");
        invoice.setPurchaseRequestValidatedAt(ORIGINAL_VALIDATED_AT);
        invoice.setInvoiceSum(BigDecimal.TEN);
        invoice.setInvoiceSumPaid(BigDecimal.ZERO);
        invoice.setInvoiceStatus("created");
        return invoice;
    }

    private static void assertOriginalRelationship(Invoice invoice) {
        assertThat(invoice.getPurchaseRequestNumber()).isEqualTo("PR-1");
        assertThat(invoice.getSupplier()).isEqualTo("Original Supplier");
        assertThat(invoice.getPurchaseRequestValidatedAt()).isEqualTo(ORIGINAL_VALIDATED_AT);
    }

    private static Stream<Arguments> createValidationFailures() {
        return Stream.of(
                Arguments.of(PurchaseRequestIntegrationException.notFound()),
                Arguments.of(PurchaseRequestIntegrationException.notApproved()),
                Arguments.of(PurchaseRequestIntegrationException.unavailable())
        );
    }
}
