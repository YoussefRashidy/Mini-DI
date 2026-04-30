package io.github.youssefrashidy.Context.miniProject.context;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.Context.miniProject.fixtures.config.only.ConfigCounter;
import io.github.youssefrashidy.Context.miniProject.fixtures.config.only.ConfigGreeter;
import io.github.youssefrashidy.Context.miniProject.fixtures.config.only.ConfigGreetingService;
import io.github.youssefrashidy.Context.miniProject.fixtures.config.only.ConfigOnlyScanAnchor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Configuration classes - @Bean wiring and scopes")
public class ConfigurationClassTest {

    @Test
    @DisplayName("@Bean methods are wired and accessible by type")
    void configurationBeansAreWired() {
        ApplicationContext ctx = new AnnotationConfigApplicationContext(ConfigOnlyScanAnchor.class);

        ConfigGreeter greeter = ctx.getInstance(ConfigGreeter.class);
        assertEquals("hello-config", greeter.message());

        ConfigGreetingService greetingService = ctx.getInstance(ConfigGreetingService.class);
        assertEquals("hello-config", greetingService.message());
    }

    @Test
    @DisplayName("@Bean identifier fallback uses method name when value is empty")
    void beanIdentifiersIncludeMethodName() {
        ApplicationContext ctx = new AnnotationConfigApplicationContext(ConfigOnlyScanAnchor.class);

        assertTrue(ctx.getBeanIdentifiers().contains("configGreeting"));
        assertTrue(ctx.getBeanIdentifiers().contains("greeter"));
        assertTrue(ctx.getBeanIdentifiers().contains("configCounter"));
    }

    @Test
    @DisplayName("@Bean scope=PROTOTYPE returns a new instance on each lookup")
    void prototypeBeanFromConfigurationReturnsDistinctInstances() {
        ApplicationContext ctx = new AnnotationConfigApplicationContext(ConfigOnlyScanAnchor.class);

        ConfigCounter c1 = ctx.getInstance(ConfigCounter.class);
        ConfigCounter c2 = ctx.getInstance(ConfigCounter.class);

        assertNotSame(c1, c2);
        assertNotEquals(c1.getId(), c2.getId());
    }
}

