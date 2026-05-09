package io.github.youssefrashidy.context.comprehensive.group3;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.ApplicationContext;
import io.github.youssefrashidy.exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.annotations.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 3 — both super and sub types are components")
class Group3ComponentCollisionTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group3ComponentCollisionTests.class);
    }

    @Component
    static class Base {
        String id() {
            return "base";
        }
    }

    @Component
    static class Derived extends Base {
        @Override
        String id() {
            return "derived";
        }
    }

    @Test
    @DisplayName("H3_T1 — concrete subclass lookup resolves to the Derived singleton")
    void concreteSubclassResolves() {
        ApplicationContext ctx = newContext();

        Derived derived = ctx.getInstance(Derived.class);
        assertNotNull(derived);
        assertEquals("derived", derived.id());
        assertSame(derived, ctx.getInstance(Derived.class));
    }

    @Test
    @DisplayName("H3_T2 — supertype lookup is ambiguous when both base and derived are registered")
    void supertypeLookupIsAmbiguous() {
        ApplicationContext ctx = newContext();

        assertThrows(AmbiguousBeanException.class, () -> ctx.getInstance(Base.class));
    }
}

