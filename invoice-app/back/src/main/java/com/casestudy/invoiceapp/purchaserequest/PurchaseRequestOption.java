package com.casestudy.invoiceapp.purchaserequest;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PurchaseRequestOption(
        @JsonProperty("request_code") String requestCode,
        @JsonProperty("request_name") String requestName,
        @JsonProperty("supplier_name") String supplierName
) {
}
