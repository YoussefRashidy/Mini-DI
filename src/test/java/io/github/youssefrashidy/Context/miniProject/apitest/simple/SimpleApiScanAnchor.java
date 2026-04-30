package io.github.youssefrashidy.Context.miniProject.apitest.simple;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.annotations.Qualifier;

public final class SimpleApiScanAnchor {
    private SimpleApiScanAnchor() {
    }
}

interface SimpleGreetingService {
    String greet();
}

@Component
@Qualifier("primaryGreeting")
class PrimaryGreetingService implements SimpleGreetingService {
    @Override
    public String greet() {
        return "hello";
    }
}

@Component
class GreetingFacade {
    private final SimpleGreetingService greetingService;

    @Inject
    GreetingFacade(@Qualifier("primaryGreeting") SimpleGreetingService greetingService) {
        this.greetingService = greetingService;
    }

    String message() {
        return greetingService.greet();
    }
}

