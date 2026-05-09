package io.github.youssefrashidy.context.miniProject.fixtures.core;

import io.github.youssefrashidy.annotations.Component;

import java.util.Map;

@Component
public class TaxCalculator implements ITaxCalculator {

    private static final Map<String, Double> RATES = Map.of(
            "US", 0.08,
            "EU", 0.20,
            "EG", 0.14
    );

    @Override
    public double tax(double amount, String region) {
        return amount * RATES.getOrDefault(region, 0.0);
    }
}
