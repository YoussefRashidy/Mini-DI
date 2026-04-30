package io.github.youssefrashidy.Context.miniProject.fixtures.qualifier;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Qualifier;

@Component
@Qualifier("stripe")
public class StripeGateway implements IPaymentGateway {
    @Override
    public String charge(double amount) { return "stripe:charged:" + amount; }
}
