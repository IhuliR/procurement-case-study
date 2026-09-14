package com.casestudy.invoiceapp.invoice;

import com.casestudy.invoiceapp.auth.CurrentUserFilter;
import com.casestudy.invoiceapp.invoice.dto.InvoiceSummaryDto;
import com.casestudy.invoiceapp.invoice.dto.InvoiceUpdateDto;
import com.casestudy.invoiceapp.purchaserequest.PurchaseRequestClient;
import com.casestudy.invoiceapp.purchaserequest.PurchaseRequestOption;
import com.casestudy.invoiceapp.purchaserequest.PurchaseRequestRelationshipService;
import com.casestudy.invoiceapp.purchaserequest.PurchaseRequestRelationshipService.ValidatedRelationship;
import com.casestudy.invoiceapp.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/invoice")
public class InvoiceController {

    private static final Set<String> VALID_STATUSES = Set.of("created", "prepaid", "paid");

    private final InvoiceRepository invoices;
    private final PurchaseRequestRelationshipService purchaseRequestRelationships;
    private final PurchaseRequestClient purchaseRequestClient;

    public InvoiceController(InvoiceRepository invoices,
                             PurchaseRequestRelationshipService purchaseRequestRelationships,
                             PurchaseRequestClient purchaseRequestClient) {
        this.invoices = invoices;
        this.purchaseRequestRelationships = purchaseRequestRelationships;
        this.purchaseRequestClient = purchaseRequestClient;
    }

    @GetMapping
    public List<InvoiceSummaryDto> list(HttpServletRequest req) {
        requireFinance(req);
        return invoices.findAllSummaries();
    }

    @GetMapping("/purchase-request-options")
    public List<PurchaseRequestOption> purchaseRequestOptions(HttpServletRequest req) {
        requireFinance(req);
        return purchaseRequestClient.getInvoiceOptions();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceSummaryDto> create(
            HttpServletRequest req,
            @RequestParam("invoice_number") String invoiceNumber,
            @RequestParam("supplier") String supplier,
            @RequestParam(value = "purchase_request_number", required = false) String purchaseRequestNumber,
            @RequestParam("invoice_sum") BigDecimal invoiceSum,
            @RequestParam(value = "invoice_sum_paid", required = false) BigDecimal invoiceSumPaid,
            @RequestParam(value = "invoice_status", defaultValue = "created") String invoiceStatus,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment
    ) throws IOException {
        User user = requireFinance(req);
        requireValidStatus(invoiceStatus);
        ValidatedRelationship relationship =
                purchaseRequestRelationships.validateForCreate(purchaseRequestNumber);

        Invoice inv = new Invoice();
        inv.setInvoiceNumber(invoiceNumber);
        applyRelationship(inv, relationship);
        inv.setInvoiceSum(invoiceSum);
        inv.setInvoiceSumPaid(invoiceSumPaid == null ? BigDecimal.ZERO : invoiceSumPaid);
        inv.setInvoiceStatus(invoiceStatus);
        inv.setUploadedBy(user.getUsername());
        if (attachment != null && !attachment.isEmpty()) {
            inv.setAttachmentBytes(attachment.getBytes());
            inv.setAttachmentFilename(attachment.getOriginalFilename());
            inv.setAttachmentContentType(attachment.getContentType());
        }
        invoices.save(inv);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(inv));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public InvoiceSummaryDto update(HttpServletRequest req,
                                    @PathVariable Long id,
                                    @RequestBody InvoiceUpdateDto body) {
        requireFinance(req);
        Invoice inv = invoices.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        Optional<ValidatedRelationship> relationship =
                purchaseRequestRelationships.validateForUpdate(inv, body.purchaseRequestNumber);

        if (body.invoiceNumber != null)         inv.setInvoiceNumber(body.invoiceNumber);
        if (relationship.isPresent()) {
            applyRelationship(inv, relationship.orElseThrow());
        } else if (inv.getPurchaseRequestNumber() == null && body.supplier != null) {
            inv.setSupplier(body.supplier);
        }
        if (body.invoiceSum != null)            inv.setInvoiceSum(body.invoiceSum);
        if (body.invoiceSumPaid != null)        inv.setInvoiceSumPaid(body.invoiceSumPaid);
        if (body.invoiceStatus != null) {
            requireValidStatus(body.invoiceStatus);
            inv.setInvoiceStatus(body.invoiceStatus);
        }
        invoices.save(inv);
        return toSummary(inv);
    }

    @GetMapping("/{id}/attachment")
    public ResponseEntity<byte[]> download(HttpServletRequest req, @PathVariable Long id) {
        requireFinance(req);
        Invoice inv = invoices.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (inv.getAttachmentBytes() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No attachment");
        }
        String filename = inv.getAttachmentFilename() != null ? inv.getAttachmentFilename() : "invoice.pdf";
        String contentType = inv.getAttachmentContentType() != null ? inv.getAttachmentContentType() : MediaType.APPLICATION_PDF_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(inv.getAttachmentBytes());
    }

    // ---------- helpers ----------

    private static User requireFinance(HttpServletRequest req) {
        User user = CurrentUserFilter.currentUser(req);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (!"finance".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires role: finance");
        }
        return user;
    }

    private static void requireValidStatus(String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid status — expected one of: created, prepaid, paid"
            );
        }
    }

    private static void applyRelationship(Invoice invoice, ValidatedRelationship relationship) {
        invoice.setPurchaseRequestNumber(relationship.requestCode());
        invoice.setSupplier(relationship.supplierName());
        invoice.setPurchaseRequestValidatedAt(relationship.validatedAt());
    }

    private static InvoiceSummaryDto toSummary(Invoice i) {
        return new InvoiceSummaryDto(
                i.getId(), i.getInvoiceNumber(), i.getSupplier(), i.getPurchaseRequestNumber(),
                i.getInvoiceSum(), i.getInvoiceSumPaid(), i.getInvoiceStatus(),
                i.getAttachmentFilename(), i.getUploadedBy(), i.getCreatedAt(), i.getUpdatedAt()
        );
    }
}
