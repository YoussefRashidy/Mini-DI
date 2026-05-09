package io.github.youssefrashidy.context.miniProject.fixtures.qualifier;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Qualifier;

@Component
@Qualifier("paypal")
public class PaypalGateway implements IPaymentGateway {
    @Override
    public String charge(double amount) { return "paypal:charged:" + amount; }
}
