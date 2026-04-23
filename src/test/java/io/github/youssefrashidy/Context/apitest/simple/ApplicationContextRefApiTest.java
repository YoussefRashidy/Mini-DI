package io.github.youssefrashidy.Context.apitest.simple;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.Context.apitest.duplicate.DuplicateIdentifierScanAnchor;
import io.github.youssefrashidy.Exceptions.DuplicateBeanIdentifierException;
import io.github.youssefrashidy.Exceptions.UnregisteredDependencyException;
import java.util.Objects;

public class ApplicationContextRefApiTest {

    public static void main(String[] args) {
        getInstanceByTypeAndIdentifierReturnsSameSingleton();
        beanIdentifiersExposeRegisteredBeans();
        unknownIdentifierThrowsExpressiveError();
        wrongExpectedTypeThrowsExpressiveError();
        duplicateIdentifierFailsFastDuringInitialization();
        System.out.println("ApplicationContextRef API probe passed.");
    }

    private static void getInstanceByTypeAndIdentifierReturnsSameSingleton() {
        ApplicationContext context = new AnnotationConfigApplicationContext(SimpleApiScanAnchor.class);

        GreetingFacade facade = context.getInstance(GreetingFacade.class);
        assertEquals("hello", facade.message(), "GreetingFacade message should come from injected bean.");

        SimpleGreetingService serviceByType = context.getInstance(SimpleGreetingService.class);
        SimpleGreetingService serviceByIdentifier = context.getInstance("primaryGreeting", SimpleGreetingService.class);

        assertSame(serviceByType, serviceByIdentifier, "Type and identifier lookup should return the same singleton instance.");
    }

    private static void beanIdentifiersExposeRegisteredBeans() {
        ApplicationContext context = new AnnotationConfigApplicationContext(SimpleApiScanAnchor.class);

        assertTrue(context.getBeanIdentifiers().contains("primaryGreeting"), "Expected qualifier identifier to be visible.");
        assertTrue(context.getBeanIdentifiers().contains("GreetingFacade"), "Expected class-name identifier to be visible.");
    }

    private static void unknownIdentifierThrowsExpressiveError() {
        ApplicationContext context = new AnnotationConfigApplicationContext(SimpleApiScanAnchor.class);

        UnregisteredDependencyException ex = assertThrows(
                UnregisteredDependencyException.class,
                () -> context.getInstance("missingIdentifier"),
                "Missing identifier should raise UnregisteredDependencyException."
        );

        assertTrue(ex.getMessage().contains("missingIdentifier"), "Error message should include the identifier.");
    }

    private static void wrongExpectedTypeThrowsExpressiveError() {
        ApplicationContext context = new AnnotationConfigApplicationContext(SimpleApiScanAnchor.class);

        UnregisteredDependencyException ex = assertThrows(
                UnregisteredDependencyException.class,
                () -> context.getInstance("primaryGreeting", GreetingFacade.class),
                "Wrong expected type should raise UnregisteredDependencyException."
        );

        assertTrue(ex.getMessage().contains("not assignable"), "Error message should explain type mismatch.");
    }

    private static void duplicateIdentifierFailsFastDuringInitialization() {
        DuplicateBeanIdentifierException ex = assertThrows(
                DuplicateBeanIdentifierException.class,
                () -> new AnnotationConfigApplicationContext(DuplicateIdentifierScanAnchor.class),
                "Duplicate identifiers should fail context initialization."
        );

        assertTrue(ex.getMessage().contains("duplicate bean identifier"), "Error should mention duplicate identifier.");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " Expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
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





