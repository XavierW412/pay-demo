package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.simulator.domain.MockProduct;

public record MockProductResponse(String productCode, String displayName, String amount, String currency, String description) {
    public static MockProductResponse from(MockProduct product) {
        return new MockProductResponse(
                product.productCode(),
                product.displayName(),
                product.amount().setScale(2).toPlainString(),
                product.currency(),
                product.description());
    }
}
