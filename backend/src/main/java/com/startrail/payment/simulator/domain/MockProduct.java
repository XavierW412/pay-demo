package com.startrail.payment.simulator.domain;

import java.math.BigDecimal;

public record MockProduct(String productCode, String displayName, BigDecimal amount, String currency, String description) {
}
