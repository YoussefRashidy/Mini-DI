package io.github.youssefrashidy.context.miniProject.context;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.ApplicationContext;
import io.github.youssefrashidy.exceptions.UnregisteredDependencyException;
import io.github.youssefrashidy.context.miniProject.fixtures.core.DiscountService;
import io.github.youssefrashidy.context.miniProject.fixtures.core.PricingEngine;
import io.github.youssefrashidy.context.miniProject.fixtures.core.TaxCalculator;
import io.github.youssefrashidy.context.miniProject.fixtures.core.IDiscountService;
import io.github.youssefrashidy.context.miniProject.fixtures.core.IPricingEngine;
import io.github.youssefrashidy.context.miniProject.fixtures.core.ITaxCalculator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Container lookup — all three getInstance overloads")
class ContainerLookupTest {

    // Scan only the core + proto fixtures, NOT the broken subpackages
    private static ApplicationContext ctx;

    @BeforeAll
    static void boot() {
        ctx = new AnnotationConfigApplicationContext(Set.of(
                "io.github.youssefrashidy.context.miniProject.fixtures.core",
                "io.github.youssefrashidy.context.miniProject.fixtures.proto"
        ));
    }

    // ── getBeanIdentifiers ────────────────────────────────────────────────────

    @Test
    @DisplayName("getBeanIdentifiers() contains all scanned beans")
    void beanIdentifiersContainsAllBeans() {
        Set<String> ids = ctx.getBeanIdentifiers();
        // resolveIdentifier = getSimpleName() when no @Qualifier on class
        assertTrue(ids.contains("PricingEngine"),       "PricingEngine");
        assertTrue(ids.contains("TaxCalculator"),       "TaxCalculator");
        assertTrue(ids.contains("DiscountService"),     "DiscountService");
        assertTrue(ids.contains("NotificationService"), "NotificationService");
        assertTrue(ids.contains("OrderService"),        "OrderService");
        assertTrue(ids.contains("SessionFactory"),      "SessionFactory");
    }

    // ── getInstance(Class) ────────────────────────────────────────────────────

    @Test
    @DisplayName("getInstance(Class) resolves concrete singleton by class")
    void getInstanceByConcreteClass() {
        PricingEngine pe = ctx.getInstance(PricingEngine.class);
        assertNotNull(pe);
    }

    @Test
    @DisplayName("getInstance(Class) resolves singleton by interface when single impl exists")
    void getInstanceByInterface() {
        IPricingEngine pe = ctx.getInstance(IPricingEngine.class);
        assertNotNull(pe);
        assertInstanceOf(PricingEngine.class, pe);
    }

    @Test
    @DisplayName("getInstance(Class) returns same instance on repeated calls — singleton identity")
    void singletonIdentityByClass() {
        PricingEngine a = ctx.getInstance(PricingEngine.class);
        PricingEngine b = ctx.getInstance(PricingEngine.class);
        assertSame(a, b, "SINGLETON scope must return the same reference");
    }

    @Test
    @DisplayName("getInstance(Class) on unknown type throws UnregisteredDependencyException")
    void unknownTypeThrows() {
        assertThrows(UnregisteredDependencyException.class,
                () -> ctx.getInstance(String.class));
    }

    // ── getInstance(String) ───────────────────────────────────────────────────

    @Test
    @DisplayName("getInstance(String) resolves by identifier = getSimpleName()")
    void getInstanceByStringId() {
        Object pe = ctx.getInstance("PricingEngine");
        assertNotNull(pe);
        assertInstanceOf(PricingEngine.class, pe);
    }

    @Test
    @DisplayName("getInstance(String) with unknown id throws UnregisteredDependencyException")
    void unknownIdThrows() {
        assertThrows(UnregisteredDependencyException.class,
                () -> ctx.getInstance("pricingEngine")); // camelCase — wrong, id is getSimpleName()
    }

    // ── getInstance(String, Class) ────────────────────────────────────────────

    @Test
    @DisplayName("getInstance(String, Class) resolves and type-checks correctly")
    void getInstanceByIdAndType() {
        TaxCalculator tc = ctx.getInstance("TaxCalculator", TaxCalculator.class);
        assertNotNull(tc);
    }

    @Test
    @DisplayName("getInstance(String, Class) with wrong expectedType throws UnregisteredDependencyException")
    void getInstanceTypeMismatchThrows() {
        assertThrows(UnregisteredDependencyException.class,
                () -> ctx.getInstance("TaxCalculator", PricingEngine.class));
    }

    // ── Interface mapped into typeIndex by registerBean ───────────────────────

    @Test
    @DisplayName("IDiscountService resolves to DiscountService via typeIndex")
    void interfaceTypeIndexResolution() {
        IDiscountService ds = ctx.getInstance(IDiscountService.class);
        assertInstanceOf(DiscountService.class, ds);
    }

    @Test
    @DisplayName("ITaxCalculator resolves to TaxCalculator via typeIndex")
    void taxInterfaceResolution() {
        ITaxCalculator tc = ctx.getInstance(ITaxCalculator.class);
        assertInstanceOf(TaxCalculator.class, tc);
    }
}
