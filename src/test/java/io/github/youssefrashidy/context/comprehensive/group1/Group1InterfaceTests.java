package io.github.youssefrashidy.context.comprehensive.group1;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.ApplicationContext;
import io.github.youssefrashidy.exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.annotations.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 1 — interface-based injection")
class Group1InterfaceTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group1InterfaceTests.class);
    }

    @Component
    static class EnglishGreeter implements Greeter {
        @Override
        public String greet() {
            return "Hello";
        }
    }

    interface Greeter {
        String greet();
    }

    @Component
    static class SpanishGreeter implements Greeter {
        @Override
        public String greet() {
            return "Hola";
        }
    }

//    @Test
//    @DisplayName("G1_T1 — single interface implementation resolves automatically")
//    void singleImplementationResolvesByInterface() {
//        ApplicationContext ctx = newContext();
//
//        Greeter greeter = ctx.getInstance(Greeter.class);
//
//        assertNotNull(greeter);
//        assertEquals("Hello", greeter.greet());
//        assertInstanceOf(EnglishGreeter.class, greeter);
//    }

    @Test
    @DisplayName("G1_T2 — adding a second implementation makes interface lookup ambiguous")
    void secondImplementationMakesInterfaceLookupAmbiguous() {
        ApplicationContext ctx = newContext();

        assertThrows(AmbiguousBeanException.class, () -> ctx.getInstance(Greeter.class));
    }

    @Test
    @DisplayName("G1_T3 — concrete type lookup remains unambiguous with multiple implementors")
    void concreteTypeLookupRemainsUnambiguous() {
        ApplicationContext ctx = newContext();

        EnglishGreeter english = ctx.getInstance(EnglishGreeter.class);
        SpanishGreeter spanish = ctx.getInstance(SpanishGreeter.class);

        assertEquals("Hello", english.greet());
        assertEquals("Hola", spanish.greet());
        assertSame(english, ctx.getInstance(EnglishGreeter.class));
        assertSame(spanish, ctx.getInstance(SpanishGreeter.class));
    }
}

