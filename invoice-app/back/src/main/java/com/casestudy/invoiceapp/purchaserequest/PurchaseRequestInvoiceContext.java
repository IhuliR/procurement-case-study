package com.casestudy.invoiceapp.purchaserequest;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PurchaseRequestInvoiceContext(
        @JsonProperty("request_code") String requestCode,
        @JsonProperty("supplier_name") String supplierName
) {
}
