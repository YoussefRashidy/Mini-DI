package io.github.youssefrashidy.context.miniProject.fixtures.broken.supplier;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.context.miniProject.fixtures.core.PricingEngine;

import java.util.function.Supplier;

@Component
public class BadSupplierConsumer {

    // PricingEngine is SINGLETON — wrapping it in Supplier must be rejected
    @Inject
    public BadSupplierConsumer(Supplier<PricingEngine> pe) {}
}
