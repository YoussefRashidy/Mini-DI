package io.github.youssefrashidy.Context.miniProject.context;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.Context.miniProject.fixtures.core.NotificationService;
import io.github.youssefrashidy.Context.miniProject.fixtures.core.OrderService;
import io.github.youssefrashidy.Context.miniProject.fixtures.proto.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Scope semantics — SINGLETON and PROTOTYPE")
public class ScopeTest {

    private static ApplicationContext ctx;

    @BeforeAll
    static void boot() {
        ctx = new AnnotationConfigApplicationContext(Set.of(
                "io.github.youssefrashidy.Context.miniProject.fixtures.core",
                "io.github.youssefrashidy.Context.miniProject.fixtures.proto"
        ));
    }

    // ── SINGLETON ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SINGLETON: ctx.getInstance returns identical reference every time")
    void singletonReturnsSameReference() {
        OrderService a = ctx.getInstance(OrderService.class);
        OrderService b = ctx.getInstance(OrderService.class);
        assertSame(a, b);
    }

    @Test
    @DisplayName("SINGLETON: state accumulated across calls is shared")
    void singletonStateIsShared() {
        NotificationService ns = ctx.getInstance(NotificationService.class);
        int before = ns.getSent().size();

        OrderService os = ctx.getInstance(OrderService.class);
        os.process("ORD-001", "laptop", 1, "standard", "US");
        os.process("ORD-002", "phone",  1, "premium",  "EU");

        // same NotificationService instance — both orders must appear
        assertEquals(before + 2, ns.getSent().size());
        assertTrue(ns.getSent().contains("ORD-001"));
        assertTrue(ns.getSent().contains("ORD-002"));
    }

    // ── PROTOTYPE ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PROTOTYPE: ctx.getInstance returns distinct instance every time")
    void prototypeReturnsDistinctInstances() {
        SessionFactory s1 = ctx.getInstance(SessionFactory.class);
        SessionFactory s2 = ctx.getInstance(SessionFactory.class);
        assertNotSame(s1, s2, "PROTOTYPE must produce a new instance per call");
        assertNotEquals(s1.getId(), s2.getId());
    }

    // ── Supplier<Prototype> inside a SINGLETON ────────────────────────────────

    @Test
    @DisplayName("Supplier<SessionFactory> in OrderService produces new instance per invocation")
    void supplierInSingletonProducesDistinctPrototypes() {
        OrderService os = ctx.getInstance(OrderService.class);

        // Call the Supplier twice through the facade
        OrderService.OrderResult r1 = os.process("ORD-A", "tablet",  2, "enterprise", "EG");
        OrderService.OrderResult r2 = os.process("ORD-B", "monitor", 1, "standard",   "US");

        assertNotEquals(r1.sessionId(), r2.sessionId(),
                "Each process() call must get a fresh SessionFactory (distinct UUID)");
    }

    @Test
    @DisplayName("Supplier reference inside singleton is identical — only the produced instances differ")
    void supplierReferenceIsStableButProductsAreNot() {
        OrderService os1 = ctx.getInstance(OrderService.class);
        OrderService os2 = ctx.getInstance(OrderService.class);

        // same singleton → same Supplier lambda reference
        assertSame(os1.getSessionFactory(), os2.getSessionFactory());

        // but successive get() calls produce distinct objects
        SessionFactory sf1 = os1.getSessionFactory().get();
        SessionFactory sf2 = os1.getSessionFactory().get();
        assertNotSame(sf1, sf2);
    }
}
