package io.github.youssefrashidy.Context.miniProject.fixtures.config.mixed;

import io.github.youssefrashidy.annotations.Component;

@Component
public class AuditClock {
    public String tick() {
        return Long.toString(System.nanoTime());
    }
}

