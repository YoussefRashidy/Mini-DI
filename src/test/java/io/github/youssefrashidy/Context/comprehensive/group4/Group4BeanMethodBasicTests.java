package io.github.youssefrashidy.Context.comprehensive.group4;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 4 — basic @Bean method cases")
class Group4BeanMethodBasicTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group4BeanMethodBasicTests.class);
    }

    @Configuration
    static class AppConfig {
        @Bean("databaseUrl")
        String databaseUrl() {
            return "jdbc:postgresql://localhost/test";
        }

        @Bean("maxConnections")
        Integer maxConnections() {
            return 10;
        }
    }

    @Test
    @DisplayName("B4_T1 — String bean method resolves correctly")
    void stringBeanResolves() {
        ApplicationContext ctx = newContext();

        assertEquals("jdbc:postgresql://localhost/test", ctx.getInstance(String.class));
    }

    @Test
    @DisplayName("B4_T2 — Integer bean method resolves correctly")
    void integerBeanResolves() {
        ApplicationContext ctx = newContext();

        assertEquals(10, ctx.getInstance(Integer.class));
    }

    @Test
    @DisplayName("B4_T3 — @Bean methods are singletons by default")
    void beanMethodsAreSingletonsByDefault() {
        ApplicationContext ctx = newContext();

        String firstString = ctx.getInstance(String.class);
        String secondString = ctx.getInstance(String.class);
        Integer firstInteger = ctx.getInstance(Integer.class);
        Integer secondInteger = ctx.getInstance(Integer.class);

        assertSame(firstString, secondString);
        assertSame(firstInteger, secondInteger);
    }
}

