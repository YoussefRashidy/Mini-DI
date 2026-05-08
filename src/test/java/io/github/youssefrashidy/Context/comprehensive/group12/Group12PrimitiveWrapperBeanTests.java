package io.github.youssefrashidy.Context.comprehensive.group12;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Configuration;
import io.github.youssefrashidy.annotations.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 12 — no-arg @Bean methods for primitive wrappers")
class Group12PrimitiveWrapperBeanTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group12PrimitiveWrapperBeanTests.class);
    }

    @Configuration
    public static class PrimitiveConfig {
        @Bean("timeout")
        public Long timeout() {
            return 5000L;
        }

        @Bean("featureFlag")
        public Boolean featureFlag() {
            return true;
        }

        @Bean("threshold")
        public Double threshold() {
            return 0.95;
        }
    }

    @Component
    static class PolicyEngine {
        final long timeout;
        final boolean flag;
        final double threshold;

        @Inject
        PolicyEngine(Long timeout, Boolean featureFlag, Double threshold) {
            this.timeout = timeout;
            this.flag = featureFlag;
            this.threshold = threshold;
        }
    }

    @Test
    @DisplayName("P12_T1 — the policy engine resolves from wrapper beans")
    void policyEngineResolves() {
        ApplicationContext ctx = newContext();

        assertNotNull(ctx.getInstance(PolicyEngine.class));
    }

    @Test
    @DisplayName("P12_T2 — primitive wrapper values are injected correctly")
    void primitiveWrapperValuesAreInjectedCorrectly() {
        ApplicationContext ctx = newContext();

        PolicyEngine engine = ctx.getInstance(PolicyEngine.class);
        assertEquals(5000L, engine.timeout);
        assertTrue(engine.flag);
        assertEquals(0.95, engine.threshold, 0.0000001);
    }

    @Test
    @DisplayName("P12_T3 — wrapper beans are singletons")
    void wrapperBeansAreSingletons() {
        ApplicationContext ctx = newContext();

        assertSame(ctx.getInstance(Long.class), ctx.getInstance(Long.class));
        assertSame(ctx.getInstance(Boolean.class), ctx.getInstance(Boolean.class));
        assertSame(ctx.getInstance(Double.class), ctx.getInstance(Double.class));
    }
}

