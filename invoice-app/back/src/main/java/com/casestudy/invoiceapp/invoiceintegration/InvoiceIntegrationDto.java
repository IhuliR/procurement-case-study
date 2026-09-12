package com.casestudy.invoiceapp.invoiceintegration;

import java.math.BigDecimal;

public record InvoiceIntegrationDto(
        Long id,
        String invoiceNumber,
        BigDecimal invoiceSum,
        BigDecimal invoiceSumPaid,
        String invoiceStatus
) {
}
