package io.github.youssefrashidy.Context.miniProject.context;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.Context.miniProject.fixtures.config.mixed.AuditClock;
import io.github.youssefrashidy.Context.miniProject.fixtures.config.mixed.AuditService;
import io.github.youssefrashidy.Context.miniProject.fixtures.config.mixed.MixedScanAnchor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Mixed configuration + component scanning")
public class MixedConfigurationTest {

    @Test
    @DisplayName("Configuration beans can depend on @Component beans")
    void configurationBeanUsesComponentDependency() {
        ApplicationContext ctx = new AnnotationConfigApplicationContext(MixedScanAnchor.class);

        AuditService auditService = ctx.getInstance(AuditService.class);
        AuditClock clock = ctx.getInstance(AuditClock.class);

        assertNotNull(auditService);
        assertNotNull(clock);
        assertSame(clock, auditService.getClock());
        assertTrue(auditService.stamp().startsWith("audit:"));
    }
}

