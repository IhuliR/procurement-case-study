package com.casestudy.invoiceapp.invoiceintegration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InvoiceIntegrationProperties.class)
class InvoiceIntegrationConfiguration {
}
