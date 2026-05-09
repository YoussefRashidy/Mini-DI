package io.github.youssefrashidy.context.miniProject.apitest.ambiguous;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.ApplicationContext;
import io.github.youssefrashidy.exceptions.AmbiguousBeanException;
 
public class ApplicationContextRefAmbiguousApiTest {

    public static void main(String[] args) {
        ambiguousLookupByTypeThrowsWhenMultipleBeansRegistered();
        System.out.println("ApplicationContextRef ambiguous-lookup probe passed.");
    }

    private static void ambiguousLookupByTypeThrowsWhenMultipleBeansRegistered() {
        ApplicationContext context = new AnnotationConfigApplicationContext(AmbiguousScanAnchor.class);

        AmbiguousBeanException ex = assertThrows(
                AmbiguousBeanException.class,
                () -> context.getInstance(AmbiguousGreetingService.class),
                "Expected ambiguous lookup by interface to throw AmbiguousBeanException."
        );

        assertTrue(ex.getMessage().contains("multiple beans registered"), "Error message should mention multiple beans.");
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


