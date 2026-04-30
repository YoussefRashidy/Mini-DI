package io.github.youssefrashidy.Context.miniProject.fixtures.core;

import io.github.youssefrashidy.annotations.Component;

import java.util.Map;

@Component
public class PricingEngine implements IPricingEngine {

    private static final Map<String, Double> PRICES = Map.of(
            "laptop",  999.0,
            "phone",   599.0,
            "tablet",  449.0,
            "monitor", 329.0
    );

    @Override
    public double price(String product, int qty) {
        return PRICES.getOrDefault(product, 100.0) * qty;
    }
}
