package com.casestudy.invoiceapp.purchaserequest;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "purchase-request")
public record PurchaseRequestProperties(
        String apiBaseUrl,
        String integrationKey,
        Duration connectTimeout,
        Duration responseTimeout
) {
}
