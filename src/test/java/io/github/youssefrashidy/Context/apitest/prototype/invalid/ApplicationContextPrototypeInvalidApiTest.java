package io.github.youssefrashidy.Context.apitest.prototype.invalid;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Exceptions.UnregisteredDependencyException;

public class ApplicationContextPrototypeInvalidApiTest {

    public static void main(String[] args) {
        supplierOfSingletonIsRejected();
        System.out.println("ApplicationContext prototype invalid-supplier probe passed.");
    }

    private static void supplierOfSingletonIsRejected() {
        UnregisteredDependencyException ex = assertThrows(
                UnregisteredDependencyException.class,
                () -> new AnnotationConfigApplicationContext(InvalidPrototypeSupplierScanAnchor.class),
                "Supplier of singleton dependency should fail initialization."
        );

        assertTrue(ex.getMessage().contains("non-prototype bean"),
                "Error message should explain Supplier<T> requires prototype target.");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T assertThrows(Class<T> expectedType, Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message + " Expected exception type=" + expectedType.getName());
        } catch (Throwable ex) {
            if (!expectedType.isInstance(ex)) {
                throw new AssertionError(message + " Expected exception type=" + expectedType.getName() + ", actual=" + ex.getClass().getName(), ex);
            }
            return expectedType.cast(ex);
        }
    }
}

