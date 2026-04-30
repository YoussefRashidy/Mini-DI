package io.github.youssefrashidy.Context.miniProject.fixtures.broken.ambiguous;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.Context.miniProject.fixtures.core.PricingEngine;
import io.github.youssefrashidy.Context.miniProject.fixtures.core.TaxCalculator;

@Component
public class AmbiguousBean {

    @Inject
    public AmbiguousBean(PricingEngine pe) {}

    @Inject
    public AmbiguousBean(TaxCalculator tc) {}
}
