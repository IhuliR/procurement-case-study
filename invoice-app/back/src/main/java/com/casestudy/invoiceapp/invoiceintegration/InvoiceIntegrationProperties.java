package com.casestudy.invoiceapp.invoiceintegration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invoice-integration")
public record InvoiceIntegrationProperties(String apiKey) {
}
