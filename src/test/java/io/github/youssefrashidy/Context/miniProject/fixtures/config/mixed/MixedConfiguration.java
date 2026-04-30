package io.github.youssefrashidy.Context.miniProject.fixtures.config.mixed;

import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Configuration;

@Configuration
public class MixedConfiguration {
    @Bean("auditService")
    public AuditService auditService(AuditClock clock) {
        return new AuditService(clock);
    }
}

