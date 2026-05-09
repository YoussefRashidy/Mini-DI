package io.github.youssefrashidy.context.miniProject.fixtures.config.mixed;

import io.github.youssefrashidy.annotations.Component;

@Component
public class AuditClock {
    public String tick() {
        return Long.toString(System.nanoTime());
    }
}

