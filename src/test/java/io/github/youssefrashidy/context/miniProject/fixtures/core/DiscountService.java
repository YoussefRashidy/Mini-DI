package io.github.youssefrashidy.context.miniProject.fixtures.core;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;

import java.util.Map;

@Component
public class DiscountService implements IDiscountService {

    private static final Map<String, Double> RATES = Map.of(
            "standard",   0.00,
            "premium",    0.08,
            "enterprise", 0.15
    );

    private final IPricingEngine pricingEngine;

    @Inject
    public DiscountService(IPricingEngine pricingEngine) {
        this.pricingEngine = pricingEngine;
    }

    @Override
    public double discount(double amount, String tier) {
        return amount * RATES.getOrDefault(tier, 0.0);
    }

    public IPricingEngine getPricingEngine() {
        return pricingEngine;
    }
}
