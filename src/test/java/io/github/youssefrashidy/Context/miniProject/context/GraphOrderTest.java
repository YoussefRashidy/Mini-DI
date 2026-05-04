package io.github.youssefrashidy.Context.miniProject.context;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.Context.miniProject.fixtures.core.OrderService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Graph correctness is observed indirectly: if the init order were wrong,
 * OrderService construction would fail with NullPointerException or
 * UnregisteredDependencyException because a dep would not yet be in the container.
 * Correct behaviour is proven by the context booting without exception and
 * producing correct domain results.
 */
@DisplayName("Dependency graph — init order and wiring correctness")
public class GraphOrderTest {

    private static ApplicationContext ctx;

    @BeforeAll
    static void boot() {
        ctx = new AnnotationConfigApplicationContext(Set.of(
                "io.github.youssefrashidy.Context.miniProject.fixtures.core",
                "io.github.youssefrashidy.Context.miniProject.fixtures.proto"
        ));
    }

    @Test
    @DisplayName("Context boots without exception — topological order is valid")
    void contextBootsCleanly() {
        assertNotNull(ctx);
    }

    @Test
    @DisplayName("OrderService is fully wired — process() returns correct arithmetic")
    void orderServiceArithmeticCorrect() {
        OrderService os = ctx.getInstance(OrderService.class);

        // laptop $999 × 2 = $1998, enterprise 15% off → $1698.30, US 8% tax → $1834.16
        OrderService.OrderResult r = os.process("TEST-001", "laptop", 2, "enterprise", "US");

        assertEquals("TEST-001", r.orderId());
        assertEquals(1998.0,  r.subtotal(),  0.001);
        assertEquals(299.70,  r.discount(),  0.001);
        assertEquals(135.864, r.tax(),       0.001);  // 1698.30 × 0.08
        assertEquals(1834.164, r.total(),    0.001);
    }

    @Test
    @DisplayName("All 6 beans registered — context is complete")
    void allBeansPresent() {
        Set<String> ids = ctx.getBeanIdentifiers();
        assertEquals(6, ids.size(),
                "Expected 6 beans: PricingEngine, TaxCalculator, DiscountService, " +
                "NotificationService, OrderService, SessionFactory");
    }

    @Test
    @DisplayName("Prototype bean is present in registry alongside singletons")
    void prototypeRegistered() {
        Set<String> ids = ctx.getBeanIdentifiers();
        assertTrue(ids.contains("SessionFactory"));
    }
}
