package io.github.youssefrashidy.Context.apitest.prototype.valid;

import io.github.youssefrashidy.Context.ApplicationContext;
import java.util.Objects;

public class ApplicationContextPrototypeApiTest {

    public static void main(String[] args) {
        prototypeBeanIsNotEagerlyConstructed();
        lookupByTypeAndIdentifierCreatesFreshInstances();
        supplierInjectionCreatesFreshPrototypeInstances();
        System.out.println("ApplicationContext prototype API probe passed.");
    }

    private static void prototypeBeanIsNotEagerlyConstructed() {
        new ApplicationContext(PrototypeApiScanAnchor.class);

        assertEquals(0, PrototypeTokenServiceImpl.constructorCalls(),
                "Prototype bean should not be created during context bootstrap.");
    }

    private static void lookupByTypeAndIdentifierCreatesFreshInstances() {
        ApplicationContext context = new ApplicationContext(PrototypeApiScanAnchor.class);

        PrototypeTokenService byType1 = context.getInstance(PrototypeTokenService.class);
        PrototypeTokenService byType2 = context.getInstance(PrototypeTokenService.class);
        PrototypeTokenService byIdentifier = context.getInstance("PrototypeTokenServiceImpl", PrototypeTokenService.class);

        assertNotSame(byType1, byType2, "Type lookup for prototype should return a new object each time.");
        assertNotSame(byType2, byIdentifier, "Identifier lookup for prototype should return a new object each time.");
        assertEquals(3, PrototypeTokenServiceImpl.constructorCalls(),
                "Three lookups should construct three prototype instances.");
    }

    private static void supplierInjectionCreatesFreshPrototypeInstances() {
        ApplicationContext context = new ApplicationContext(PrototypeApiScanAnchor.class);
        PrototypeConsumer consumer = context.getInstance(PrototypeConsumer.class);

        int firstSerial = consumer.nextTokenSerial();
        int secondSerial = consumer.nextTokenSerial();

        assertTrue(secondSerial > firstSerial,
                "Supplier-injected prototype should provide distinct instances with increasing serials.");
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

    private static void assertNotSame(Object first, Object second, String message) {
        if (first == second) {
            throw new AssertionError(message);
        }
    }
}

