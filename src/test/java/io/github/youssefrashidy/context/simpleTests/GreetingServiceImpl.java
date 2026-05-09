package io.github.youssefrashidy.context.simpleTests;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@Singleton
public class GreetingServiceImpl implements GreetingService {
    private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

    GreetingServiceImpl() {
        CONSTRUCTOR_CALLS.incrementAndGet();
    }

    static int constructorCalls() {
        return CONSTRUCTOR_CALLS.get();
    }

    @Override
    public String greet() {
        return "Hello from eager init";
    }
}

