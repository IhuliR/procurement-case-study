package com.casestudy.invoiceapp.invoice;

import com.casestudy.invoiceapp.invoice.dto.InvoiceSummaryDto;
import com.casestudy.invoiceapp.invoiceintegration.InvoiceIntegrationDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Projection query that doesn't touch the attachment_bytes column —
     * keeps the list response small even when invoices have multi-MB PDFs.
     */
    @Query("""
        select new com.casestudy.invoiceapp.invoice.dto.InvoiceSummaryDto(
            i.id, i.invoiceNumber, i.supplier, i.purchaseRequestNumber,
            i.invoiceSum, i.invoiceSumPaid, i.invoiceStatus,
            i.attachmentFilename, i.uploadedBy, i.createdAt, i.updatedAt
        )
        from Invoice i
        order by i.id desc
    """)
    List<InvoiceSummaryDto> findAllSummaries();

    @Query("""
        select new com.casestudy.invoiceapp.invoiceintegration.InvoiceIntegrationDto(
            i.id, i.invoiceNumber, i.invoiceSum, i.invoiceSumPaid, i.invoiceStatus
        )
        from Invoice i
        where i.purchaseRequestNumber = :purchaseRequestNumber
          and i.purchaseRequestValidatedAt is not null
        order by i.id asc
    """)
    List<InvoiceIntegrationDto> findValidatedByPurchaseRequestNumber(
            @Param("purchaseRequestNumber") String purchaseRequestNumber
    );
}
