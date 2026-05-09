package io.github.youssefrashidy.context.simpleTests;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.ApplicationContext;

/**
 * Small runnable probe for verifying eager initialization in ApplicationContextRef.
 */
public class EagerInitializationTest {

    public static void main(String[] args) {
        ApplicationContext context = new AnnotationConfigApplicationContext(EagerInitializationTest.class);

        if (GreetingServiceImpl.constructorCalls() != 1) {
            throw new AssertionError("Expected eager initialization to construct GreetingServiceImpl once, but got "
                    + GreetingServiceImpl.constructorCalls());
        }

        GreetingService service = context.getInstance(GreetingService.class);
        if (GreetingServiceImpl.constructorCalls() != 1) {
            throw new AssertionError("Expected cached singleton instance after getInstance, but constructor ran "
                    + GreetingServiceImpl.constructorCalls() + " times.");
        }

        System.out.println(service.greet());
        System.out.println("Eager initialization verified successfully.");
    }
}


