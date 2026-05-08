package io.github.youssefrashidy.Context.comprehensive.group7;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Configuration;
import io.github.youssefrashidy.annotations.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 7 — mixed parameter constructor stress test")
class Group7MixedConstructorTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group7MixedConstructorTests.class);
    }

    @Component
    static class ServiceA {
        String name() {
            return "A";
        }
    }

    @Component
    static class ServiceB {
        String name() {
            return "B";
        }
    }

    @Configuration
    static class MixedConfig {
        @Bean("serviceTag")
        String serviceTag() {
            return "prod";
        }
    }

    @Component
    static class Orchestrator {
        final ServiceA a;
        final ServiceB b;
        final String tag;

        @Inject
        Orchestrator(ServiceA a, ServiceB b, String tag) {
            this.a = a;
            this.b = b;
            this.tag = tag;
        }

        String describe() {
            return a.name() + "+" + b.name() + "@" + tag;
        }
    }

    @Test
    @DisplayName("M7_T1 — the mixed constructor resolves without error")
    void mixedConstructorResolves() {
        ApplicationContext ctx = newContext();

        assertNotNull(ctx.getInstance(Orchestrator.class));
    }

    @Test
    @DisplayName("M7_T2 — the mixed constructor wires all values correctly")
    void mixedConstructorDescribesCorrectly() {
        ApplicationContext ctx = newContext();

        Orchestrator orchestrator = ctx.getInstance(Orchestrator.class);
        assertEquals("A+B@prod", orchestrator.describe());
    }

    @Test
    @DisplayName("M7_T3 — ServiceA is the same singleton inside Orchestrator and in direct lookup")
    void orchestratorSharesServiceAInstance() {
        ApplicationContext ctx = newContext();

        Orchestrator orchestrator = ctx.getInstance(Orchestrator.class);
        assertSame(ctx.getInstance(ServiceA.class), orchestrator.a);
    }

    @Test
    @DisplayName("M7_T4 — ServiceB is the same singleton inside Orchestrator and in direct lookup")
    void orchestratorSharesServiceBInstance() {
        ApplicationContext ctx = newContext();

        Orchestrator orchestrator = ctx.getInstance(Orchestrator.class);
        assertSame(ctx.getInstance(ServiceB.class), orchestrator.b);
    }

    @Test
    @DisplayName("M7_T5 — the config bean String is reused inside Orchestrator")
    void orchestratorSharesTagInstance() {
        ApplicationContext ctx = newContext();

        Orchestrator orchestrator = ctx.getInstance(Orchestrator.class);
        assertSame(ctx.getInstance(String.class), orchestrator.tag);
    }
}

