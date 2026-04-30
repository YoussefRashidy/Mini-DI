package io.github.youssefrashidy.Context.miniProject.context;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.Exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.Context.miniProject.fixtures.core.DiscountService;
import io.github.youssefrashidy.Context.miniProject.fixtures.core.IPricingEngine;
import io.github.youssefrashidy.Context.miniProject.fixtures.core.PricingEngine;
import io.github.youssefrashidy.Context.miniProject.fixtures.qualifier.IPaymentGateway;
import io.github.youssefrashidy.Context.miniProject.fixtures.qualifier.PaymentProcessor;
import io.github.youssefrashidy.Context.miniProject.fixtures.qualifier.StripeGateway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Interface resolution and @Qualifier disambiguation")
public class InterfaceResolutionTest {

    // Qualifier fixtures add two IPaymentGateway impls — use a dedicated ctx
    private static ApplicationContext coreCtx;
    private static ApplicationContext qualifierCtx;

    @BeforeAll
    static void boot() {
        coreCtx = new AnnotationConfigApplicationContext(Set.of(
                "io.github.youssefrashidy.Context.fixtures.core",
                "io.github.youssefrashidy.Context.fixtures.proto"
        ));
        qualifierCtx = new AnnotationConfigApplicationContext(Set.of(
                "io.github.youssefrashidy.Context.fixtures.qualifier"
        ));
    }

    // ── Single implementation ─────────────────────────────────────────────────

    @Test
    @DisplayName("Interface with single impl resolved automatically (no @Qualifier needed)")
    void singleImplResolvedByInterface() {
        IPricingEngine pe = coreCtx.getInstance(IPricingEngine.class);
        assertInstanceOf(PricingEngine.class, pe);
    }

    @Test
    @DisplayName("Injected interface dep is same singleton as direct lookup")
    void injectedInterfaceIsSameSingleton() {
        // DiscountService receives IPricingEngine via constructor injection
        DiscountService ds  = coreCtx.getInstance(DiscountService.class);
        PricingEngine   pe  = coreCtx.getInstance(PricingEngine.class);
        assertSame(pe, ds.getPricingEngine(),
                "Injected IPricingEngine must be the same singleton as the direct lookup");
    }

    // ── @Qualifier disambiguation ─────────────────────────────────────────────

    @Test
    @DisplayName("@Qualifier(\"stripe\") on param selects StripeGateway among two IPaymentGateway impls")
    void qualifierSelectsCorrectImpl() {
        PaymentProcessor pp = qualifierCtx.getInstance(PaymentProcessor.class);
        assertInstanceOf(StripeGateway.class, pp.getGateway());
    }

    @Test
    @DisplayName("PaymentProcessor.pay() delegates to StripeGateway")
    void qualifierWiredBehaviourCorrect() {
        PaymentProcessor pp = qualifierCtx.getInstance(PaymentProcessor.class);
        String result = pp.pay(100.0);
        assertTrue(result.startsWith("stripe:"), "Expected stripe gateway to handle the charge");
    }

    @Test
    @DisplayName("@Qualifier on class sets identifier — resolveIdentifier uses annotation value")
    void qualifierOnClassSetsIdentifier() {
        Set<String> ids = qualifierCtx.getBeanIdentifiers();
        assertTrue(ids.contains("stripe"), "StripeGateway @Qualifier(\"stripe\") must produce id=\"stripe\"");
        assertTrue(ids.contains("paypal"), "PaypalGateway @Qualifier(\"paypal\") must produce id=\"paypal\"");
        assertFalse(ids.contains("StripeGateway"), "getSimpleName() must be overridden by @Qualifier");
    }

    @Test
    @DisplayName("getInstance(Class) on ambiguous interface throws AmbiguousBeanException")
    void ambiguousInterfaceLookupThrows() {
        // Two IPaymentGateway impls, no qualifier on the call site
        assertThrows(AmbiguousBeanException.class,
                () -> qualifierCtx.getInstance(IPaymentGateway.class));
    }

    @Test
    @DisplayName("getInstance(String, Class) with explicit qualifier id resolves correctly")
    void explicitQualifierIdResolvesGateway() {
        IPaymentGateway gw = qualifierCtx.getInstance("stripe", IPaymentGateway.class);
        assertInstanceOf(StripeGateway.class, gw);
    }
}
