package com.casestudy.invoiceapp.invoiceintegration;

import com.casestudy.invoiceapp.auth.CurrentUserFilter;
import com.casestudy.invoiceapp.invoice.Invoice;
import com.casestudy.invoiceapp.invoice.InvoiceRepository;
import com.casestudy.invoiceapp.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:invoice-integration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "invoice-integration.api-key=test-invoice-integration-key"
})
class InvoiceIntegrationControllerTest {

    private static final String URL = "/integration/invoices";
    private static final String HEADER = "X-Integration-Key";
    private static final String KEY = "test-invoice-integration-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceRepository invoices;

    @Test
    void missingIntegrationKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get(URL).param("purchase_request_number", "PR-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void incorrectIntegrationKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get(URL)
                        .header(HEADER, "wrong-key")
                        .param("purchase_request_number", "PR-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyIntegrationKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get(URL)
                        .header(HEADER, "  ")
                        .param("purchase_request_number", "PR-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctIntegrationKeyAllowsRequest() throws Exception {
        mockMvc.perform(integrationRequest("PR-1"))
                .andExpect(status().isOk());
    }

    @Test
    void blankPurchaseRequestNumberIsBadRequest() throws Exception {
        mockMvc.perform(integrationRequest("  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRelationshipReturnsEmptyArray() throws Exception {
        mockMvc.perform(integrationRequest("PR-404"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void validatedRelationshipReturnsContractDto() throws Exception {
        Invoice invoice = invoices.saveAndFlush(invoice(
                "INV-1", "PR-1", Instant.parse("2026-01-01T00:00:00Z"),
                "1000.00", "250.00", "prepaid"
        ));

        mockMvc.perform(integrationRequest("PR-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(invoice.getId()))
                .andExpect(jsonPath("$[0].invoice_number").value("INV-1"))
                .andExpect(jsonPath("$[0].invoice_sum").value(1000.00))
                .andExpect(jsonPath("$[0].invoice_sum_paid").value(250.00))
                .andExpect(jsonPath("$[0].invoice_status").value("prepaid"));
    }

    @Test
    void multipleValidatedInvoicesAreReturnedInIdOrder() throws Exception {
        Invoice first = invoices.saveAndFlush(invoice(
                "INV-1", "PR-1", Instant.parse("2026-01-01T00:00:00Z"),
                "100.00", "0.00", "created"
        ));
        Invoice second = invoices.saveAndFlush(invoice(
                "INV-2", "PR-1", Instant.parse("2026-01-02T00:00:00Z"),
                "200.00", "200.00", "paid"
        ));

        mockMvc.perform(integrationRequest("PR-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(second.getId()));
    }

    @Test
    void invoiceForAnotherPurchaseRequestIsNotReturned() throws Exception {
        invoices.saveAndFlush(invoice(
                "INV-2", "PR-2", Instant.parse("2026-01-01T00:00:00Z"),
                "100.00", "0.00", "created"
        ));

        mockMvc.perform(integrationRequest("PR-1"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void legacyUnvalidatedInvoiceIsNotReturned() throws Exception {
        invoices.saveAndFlush(invoice(
                "INV-LEGACY", "PR-1", null, "100.00", "0.00", "created"
        ));

        mockMvc.perform(integrationRequest("PR-1"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void responseDoesNotExposeCredentialsOrUnnecessaryInvoiceFields() throws Exception {
        invoices.saveAndFlush(invoice(
                "INV-1", "PR-1", Instant.parse("2026-01-01T00:00:00Z"),
                "100.00", "0.00", "created"
        ));

        mockMvc.perform(integrationRequest("PR-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].supplier").doesNotExist())
                .andExpect(jsonPath("$[0].purchase_request_number").doesNotExist())
                .andExpect(jsonPath("$[0].uploaded_by").doesNotExist())
                .andExpect(jsonPath("$[0].attachment_filename").doesNotExist())
                .andExpect(jsonPath("$[0].integration_key").doesNotExist())
                .andExpect(content().string(not(containsString(KEY))))
                .andExpect(content().string(not(containsString(HEADER))));
    }

    @Test
    void integrationKeyDoesNotAuthorizeUserInvoiceEndpoint() throws Exception {
        mockMvc.perform(get("/invoice").header(HEADER, KEY))
                .andExpect(status().isUnauthorized());

        User financeUser = mock(User.class);
        when(financeUser.getRole()).thenReturn("finance");
        mockMvc.perform(get("/invoice")
                        .requestAttr(CurrentUserFilter.CURRENT_USER_ATTR, financeUser))
                .andExpect(status().isOk());
    }

    @Test
    void unconfiguredIntegrationKeyFailsClosed() throws Exception {
        InvoiceRepository repository = mock(InvoiceRepository.class);
        InvoiceIntegrationController controller = new InvoiceIntegrationController(
                repository,
                new InvoiceIntegrationProperties("")
        );
        MockMvc standaloneMockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        standaloneMockMvc.perform(get(URL)
                        .header(HEADER, KEY)
                        .param("purchase_request_number", "PR-1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    integrationRequest(String purchaseRequestNumber) {
        return get(URL)
                .header(HEADER, KEY)
                .param("purchase_request_number", purchaseRequestNumber);
    }

    private static Invoice invoice(String invoiceNumber,
                                   String purchaseRequestNumber,
                                   Instant validatedAt,
                                   String invoiceSum,
                                   String paidSum,
                                   String status) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setSupplier("Supplier");
        invoice.setPurchaseRequestNumber(purchaseRequestNumber);
        invoice.setPurchaseRequestValidatedAt(validatedAt);
        invoice.setInvoiceSum(new BigDecimal(invoiceSum));
        invoice.setInvoiceSumPaid(new BigDecimal(paidSum));
        invoice.setInvoiceStatus(status);
        return invoice;
    }
}
