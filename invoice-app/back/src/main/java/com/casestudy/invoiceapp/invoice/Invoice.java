package com.casestudy.invoiceapp.invoice;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(nullable = false)
    private String supplier;

    /**
     * Free-text purchase request code (e.g. "PR-2"). There is no foreign key
     * back to the PR app's purchase_requests table — that's one of the rough
     * edges candidates are expected to call out.
     */
    @Column(name = "purchase_request_number")
    private String purchaseRequestNumber;

    @Column(name = "purchase_request_validated_at")
    private Instant purchaseRequestValidatedAt;

    @Column(name = "invoice_sum", precision = 14, scale = 2, nullable = false)
    private BigDecimal invoiceSum = BigDecimal.ZERO;

    @Column(name = "invoice_sum_paid", precision = 14, scale = 2, nullable = false)
    private BigDecimal invoiceSumPaid = BigDecimal.ZERO;

    /** 'created' | 'prepaid' | 'paid'. Validated in the controller. */
    @Column(name = "invoice_status", nullable = false)
    private String invoiceStatus = "created";

    @Column(name = "attachment_filename")
    private String attachmentFilename;

    @Column(name = "attachment_content_type")
    private String attachmentContentType;

    /**
     * PDF blob. Stored as Postgres {@code bytea}, not {@code oid} — Hibernate 6
     * would default {@code @Lob byte[]} to oid, which requires transaction
     * scope on reads and complicates the streaming download. The list endpoint
     * dodges loading this column via a projection query.
     */
    @Column(name = "attachment_bytes", columnDefinition = "bytea")
    private byte[] attachmentBytes;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public String getPurchaseRequestNumber() { return purchaseRequestNumber; }
    public void setPurchaseRequestNumber(String purchaseRequestNumber) { this.purchaseRequestNumber = purchaseRequestNumber; }

    public Instant getPurchaseRequestValidatedAt() { return purchaseRequestValidatedAt; }
    public void setPurchaseRequestValidatedAt(Instant purchaseRequestValidatedAt) { this.purchaseRequestValidatedAt = purchaseRequestValidatedAt; }

    public BigDecimal getInvoiceSum() { return invoiceSum; }
    public void setInvoiceSum(BigDecimal invoiceSum) { this.invoiceSum = invoiceSum; }

    public BigDecimal getInvoiceSumPaid() { return invoiceSumPaid; }
    public void setInvoiceSumPaid(BigDecimal invoiceSumPaid) { this.invoiceSumPaid = invoiceSumPaid; }

    public String getInvoiceStatus() { return invoiceStatus; }
    public void setInvoiceStatus(String invoiceStatus) { this.invoiceStatus = invoiceStatus; }

    public String getAttachmentFilename() { return attachmentFilename; }
    public void setAttachmentFilename(String attachmentFilename) { this.attachmentFilename = attachmentFilename; }

    public String getAttachmentContentType() { return attachmentContentType; }
    public void setAttachmentContentType(String attachmentContentType) { this.attachmentContentType = attachmentContentType; }

    public byte[] getAttachmentBytes() { return attachmentBytes; }
    public void setAttachmentBytes(byte[] attachmentBytes) { this.attachmentBytes = attachmentBytes; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
