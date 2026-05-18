package io.github.youssefrashidy.context.miniProject.context;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.miniProject.fixtures.core.PricingEngine;
import io.github.youssefrashidy.exceptions.AmbiguousConstructorException;
import io.github.youssefrashidy.exceptions.CircularDependencyException;
import io.github.youssefrashidy.exceptions.UnregisteredDependencyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Exception paths — all failure modes")
public class ExceptionPathsTest {

    // ── Circular dependency ───────────────────────────────────────────────────

    @Test
    @DisplayName("A → B → A circular dep throws CircularDependencyException at context init")
    void circularDependencyThrows() {
        CircularDependencyException ex = assertThrows(
                CircularDependencyException.class,
                () -> new AnnotationConfigApplicationContext(Set.of(
                        "io.github.youssefrashidy.context.miniProject.fixtures.broken.circular"
                ))
        );
        String msg = ex.getMessage();
        assertTrue(msg.contains("circular dependency"), "Message must mention circular dependency");
        assertTrue(msg.contains("BeanA") || msg.contains("BeanB"),
                "Message must name the offending beans");
    }

    // ── Ambiguous constructor ─────────────────────────────────────────────────

    @Test
    @DisplayName("Two @Inject constructors on same class throws AmbiguousConstructorException")
    void ambiguousConstructorThrows() {
        AmbiguousConstructorException ex = assertThrows(
                AmbiguousConstructorException.class,
                () -> new AnnotationConfigApplicationContext(Set.of(
                        "io.github.youssefrashidy.context.miniProject.fixtures.broken.ambiguous",
                        "io.github.youssefrashidy.context.miniProject.fixtures.core",   // AmbiguousBean depends on these
                        "io.github.youssefrashidy.context.miniProject.fixtures.proto"
                ))
        );
        assertTrue(ex.getMessage().contains("AmbiguousBean") ||
                   ex.getMessage().contains("2 constructors"));
    }

    // ── Primitive injection ───────────────────────────────────────────────────

    @Test
    @DisplayName("Primitive (int) constructor param throws UnregisteredDependencyException")
    void primitiveInjectionThrows() {
        UnregisteredDependencyException ex = assertThrows(
                UnregisteredDependencyException.class,
                () -> new AnnotationConfigApplicationContext(Set.of(
                        "io.github.youssefrashidy.context.miniProject.fixtures.broken.primitive"
                ))
        );
        assertTrue(ex.getMessage().contains("int") || ex.getMessage().contains("primitive"),
                "Error must mention the unsupported type");
    }

    // ── Supplier wrapping a non-prototype ─────────────────────────────────────

    @Test
    @DisplayName("Supplier<Singleton> injection throws UnregisteredDependencyException")
    void supplierWrappingNonPrototypeThrows() {
        UnregisteredDependencyException ex = assertThrows(
                UnregisteredDependencyException.class,
                () -> new AnnotationConfigApplicationContext(Set.of(
                        "io.github.youssefrashidy.context.miniProject.fixtures.broken.supplier",
                        "io.github.youssefrashidy.context.miniProject.fixtures.core",   // PricingEngine lives here
                        "io.github.youssefrashidy.context.miniProject.fixtures.proto"
                ))
        );
        assertTrue(ex.getMessage().contains("non-prototype") || ex.getMessage().contains("PROTOTYPE"),
                "Error must mention the prototype constraint");
    }

    // ── Unknown identifier at runtime ─────────────────────────────────────────

    @Test
    @DisplayName("getInstance(String) with unknown id throws UnregisteredDependencyException")
    void unknownIdentifierAtRuntimeThrows() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Set.of(
                "io.github.youssefrashidy.context.miniProject.fixtures.core",
                "io.github.youssefrashidy.context.miniProject.fixtures.proto"
        ));
        UnregisteredDependencyException ex = assertThrows(
                UnregisteredDependencyException.class,
                () -> ctx.getInstance("doesNotExist")
        );
        assertTrue(ex.getMessage().contains("doesNotExist"),
                "Error message should echo the unknown identifier");
    }

    // ── Wrong expected type ───────────────────────────────────────────────────

    @Test
    @DisplayName("getInstance(String, Class) with mismatched type throws UnregisteredDependencyException")
    void typeMismatchThrows() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Set.of(
                "io.github.youssefrashidy.context.miniProject.fixtures.core",
                "io.github.youssefrashidy.context.miniProject.fixtures.proto"
        ));
        assertThrows(
                UnregisteredDependencyException.class,
                () -> ctx.getInstance("TaxCalculator",
                        PricingEngine.class)
        );
    }
}
