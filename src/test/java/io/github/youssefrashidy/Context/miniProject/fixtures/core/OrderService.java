package io.github.youssefrashidy.Context.miniProject.fixtures.core;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.Context.miniProject.fixtures.proto.SessionFactory;

import java.util.function.Supplier;

@Component
public class OrderService {

    private final IPricingEngine    pricingEngine;
    private final ITaxCalculator    taxCalculator;
    private final IDiscountService  discountService;
    private final NotificationService notifications;
    // Supplier so each processOrder() call gets a fresh SessionFactory instance
    private final Supplier<SessionFactory> sessionFactory;

    @Inject
    public OrderService(
            IPricingEngine pricingEngine,
            ITaxCalculator taxCalculator,
            IDiscountService discountService,
            NotificationService notifications,
            Supplier<SessionFactory> sessionFactory
    ) {
        this.pricingEngine   = pricingEngine;
        this.taxCalculator   = taxCalculator;
        this.discountService = discountService;
        this.notifications   = notifications;
        this.sessionFactory  = sessionFactory;
    }

    public record OrderResult(String orderId, double subtotal, double discount, double tax, double total, String sessionId) {}

    public OrderResult process(String orderId, String product, int qty, String tier, String region) {
        SessionFactory session = sessionFactory.get();          // new instance each call

        double subtotal  = pricingEngine.price(product, qty);
        double disc      = discountService.discount(subtotal, tier);
        double afterDisc = subtotal - disc;
        double tax       = taxCalculator.tax(afterDisc, region);
        double total     = afterDisc + tax;

        notifications.notify(orderId);
        return new OrderResult(orderId, subtotal, disc, tax, total, session.getId());
    }

    // Exposed for singleton-identity assertions in tests
    public Supplier<SessionFactory> getSessionFactory() { return sessionFactory; }
    public NotificationService getNotifications()        { return notifications; }
}
