package io.github.youssefrashidy.Context.comprehensive.group8;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.annotations.Prototype;
import io.github.youssefrashidy.annotations.Singelton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 8 — singleton/prototype scope interaction")
class Group8ScopeInteractionTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group8ScopeInteractionTests.class);
    }

    @Component
    @Prototype
    static class RequestContext {
        final long id = System.nanoTime();
    }

    @Component
    @Singelton
    static class RequestHandler {
        final RequestContext ctx;

        @Inject
        RequestHandler(RequestContext ctx) {
            this.ctx = ctx;
        }
    }

    @Component
    @Singelton
    static class SmartHandler {
        final Supplier<RequestContext> ctxSupplier;

        @Inject
        SmartHandler(Supplier<RequestContext> ctxSupplier) {
            this.ctxSupplier = ctxSupplier;
        }
    }

    @Test
    @DisplayName("S8_T1 — direct prototype injection into a singleton captures one instance")
    void directPrototypeInjectionCapturesOneInstance() {
        ApplicationContext ctx = newContext();

        RequestHandler handler = ctx.getInstance(RequestHandler.class);
        RequestHandler sameHandler = ctx.getInstance(RequestHandler.class);
        RequestContext freshPrototype = ctx.getInstance(RequestContext.class);

        assertSame(handler, sameHandler);
        assertNotSame(handler.ctx, freshPrototype);
        assertSame(handler.ctx, sameHandler.ctx);
    }

    @Test
    @DisplayName("S8_T2 — Supplier<RequestContext> produces a fresh prototype per invocation")
    void supplierProducesFreshPrototypePerInvocation() {
        ApplicationContext ctx = newContext();

        SmartHandler handler = ctx.getInstance(SmartHandler.class);
        RequestContext first = handler.ctxSupplier.get();
        RequestContext second = handler.ctxSupplier.get();

        assertNotSame(first, second);
    }

    @Test
    @DisplayName("S8_T3 — the singleton handler wrapper itself is reused")
    void singletonWrapperIsReused() {
        ApplicationContext ctx = newContext();

        assertSame(ctx.getInstance(SmartHandler.class), ctx.getInstance(SmartHandler.class));
    }
}

