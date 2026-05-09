package io.github.youssefrashidy.context.simpleTests;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.ApplicationContext;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Eager-initialization probe that keeps the service types as static inner classes.
 */
public class InnerClassEagerInitializationTest {

    public static void main(String[] args) {
        ApplicationContext context = new AnnotationConfigApplicationContext(InnerClassEagerInitializationTest.class);

        if (InnerGreetingServiceImpl.constructorCalls() != 1) {
            throw new AssertionError("Expected eager initialization to construct InnerGreetingServiceImpl once, but got "
                    + InnerGreetingServiceImpl.constructorCalls());
        }

        InnerGreetingService service = context.getInstance(InnerGreetingService.class);
        if (InnerGreetingServiceImpl.constructorCalls() != 1) {
            throw new AssertionError("Expected cached singleton instance after getInstance, but constructor ran "
                    + InnerGreetingServiceImpl.constructorCalls() + " times.");
        }

        System.out.println(service.greet());
        System.out.println("Inner-class eager initialization verified successfully.");
    }

    interface InnerGreetingService {
        String greet();
    }

    @Component
    @Singleton
    static class InnerGreetingServiceImpl implements InnerGreetingService {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        InnerGreetingServiceImpl() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static int constructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }

        @Override
        public String greet() {
            return "Hello from inner-class eager init";
        }
    }
}



