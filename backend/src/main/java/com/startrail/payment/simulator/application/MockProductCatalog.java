package com.startrail.payment.simulator.application;

import com.startrail.payment.simulator.domain.MockProduct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class MockProductCatalog {
    private static final List<MockProduct> PRODUCTS = List.of(
            new MockProduct(
                    "MOCK-CNY-SERVICE",
                    "星图基础服务（示例）",
                    new BigDecimal("299.00"),
                    "CNY",
                    "CN",
                    "本地模拟服务，展示人民币付款链路。"),
            new MockProduct(
                    "MOCK-USD-SERVICE",
                    "Startrail Standard (sample)",
                    new BigDecimal("39.00"),
                    "USD",
                    "US",
                    "Local sample service for the USD payment flow."));

    public List<MockProduct> findAll() {
        return PRODUCTS;
    }

    public Optional<MockProduct> findByCode(String productCode) {
        return PRODUCTS.stream().filter(product -> product.productCode().equals(productCode)).findFirst();
    }
}
