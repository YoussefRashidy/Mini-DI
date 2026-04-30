package io.github.youssefrashidy.Context.miniProject.apitest.ambiguous;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Qualifier;

public final class AmbiguousScanAnchor {
    private AmbiguousScanAnchor() {
    }
}

interface AmbiguousGreetingService {
    String id();
}

@Component
@Qualifier("one")
class AmbiguousGreetingOne implements AmbiguousGreetingService {
    @Override
    public String id() {
        return "one";
    }
}

@Component
@Qualifier("two")
class AmbiguousGreetingTwo implements AmbiguousGreetingService {
    @Override
    public String id() {
        return "two";
    }
}


