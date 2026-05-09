package io.github.youssefrashidy.context.miniProject.fixtures.qualifier;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.annotations.Qualifier;

@Component
public class PaymentProcessor {

    private final IPaymentGateway gateway;

    @Inject
    public PaymentProcessor(@Qualifier("stripe") IPaymentGateway gateway) {
        this.gateway = gateway;
    }

    public String pay(double amount) { return gateway.charge(amount); }

    public IPaymentGateway getGateway() { return gateway; }
}
