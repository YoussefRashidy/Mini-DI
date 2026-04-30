package io.github.youssefrashidy.Context.miniProject.fixtures.config.mixed;

public class AuditService {
    private final AuditClock clock;

    public AuditService(AuditClock clock) {
        this.clock = clock;
    }

    public String stamp() {
        return "audit:" + clock.tick();
    }

    public AuditClock getClock() {
        return clock;
    }
}

