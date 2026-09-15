package com.casestudy.invoiceapp.invoiceintegration;

import com.casestudy.invoiceapp.invoice.InvoiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/integration/invoices")
public class InvoiceIntegrationController {

    private static final String INTEGRATION_KEY_HEADER = "X-Integration-Key";

    private final InvoiceRepository invoices;
    private final InvoiceIntegrationProperties properties;

    public InvoiceIntegrationController(InvoiceRepository invoices,
                                        InvoiceIntegrationProperties properties) {
        this.invoices = invoices;
        this.properties = properties;
    }

    @GetMapping
    public List<InvoiceIntegrationDto> listByPurchaseRequest(
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String providedKey,
            @RequestParam(value = "purchase_request_number", required = false)
            String purchaseRequestNumber
    ) {
        requireIntegrationKey(providedKey);
        if (purchaseRequestNumber == null || purchaseRequestNumber.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Purchase request number is required"
            );
        }
        return invoices.findValidatedByPurchaseRequestNumber(purchaseRequestNumber);
    }

    private void requireIntegrationKey(String providedKey) {
        String configuredKey = properties.apiKey();
        if (configuredKey == null || configuredKey.isBlank()
                || providedKey == null || providedKey.isBlank()
                || !MessageDigest.isEqual(
                        configuredKey.getBytes(StandardCharsets.UTF_8),
                        providedKey.getBytes(StandardCharsets.UTF_8)
                )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
    }
}
