package io.github.youssefrashidy.Context.comprehensive.group11;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.Exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 11 — @Bean and @Component registering the same type")
class Group11CollisionTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group11CollisionTests.class);
    }

    @Component
    static class Logger {
        String log() {
            return "component-logger";
        }
    }

    @Configuration
    static class LogConfig {
        @Bean("logger")
        Logger logger() {
            return new Logger() {
                @Override
                String log() {
                    return "bean-logger";
                }
            };
        }
    }

    @Test
    @DisplayName("D11_T1 — class-based lookup is ambiguous when a component and a bean share the same type")
    void classBasedLookupIsAmbiguous() {
        ApplicationContext ctx = newContext();

        assertThrows(AmbiguousBeanException.class, () -> ctx.getInstance(Logger.class));
    }

    @Test
    @DisplayName("D11_T2 — the identifier resolves the intended bean deterministically")
    void identifierResolvesIntendedBean() {
        ApplicationContext ctx = newContext();

        Logger componentLogger = ctx.getInstance("Logger", Logger.class);
        Logger beanLogger = ctx.getInstance("logger", Logger.class);

        assertEquals("component-logger", componentLogger.log());
        assertEquals("bean-logger", beanLogger.log());
        assertNotSame(componentLogger, beanLogger);
    }
}

