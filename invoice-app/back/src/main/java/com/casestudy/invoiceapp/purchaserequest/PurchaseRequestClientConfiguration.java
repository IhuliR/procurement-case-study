package com.casestudy.invoiceapp.purchaserequest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PurchaseRequestProperties.class)
class PurchaseRequestClientConfiguration {

    @Bean
    PurchaseRequestClient purchaseRequestClient(RestClient.Builder builder,
                                                 PurchaseRequestProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.responseTimeout());

        RestClient restClient = builder
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(requestFactory)
                .build();
        return new PurchaseRequestClient(restClient, properties.integrationKey());
    }
}
