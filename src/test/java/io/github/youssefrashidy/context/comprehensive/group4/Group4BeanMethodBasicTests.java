package io.github.youssefrashidy.context.comprehensive.group4;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.ApplicationContext;
import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 4 — basic @Bean method cases")
public class Group4BeanMethodBasicTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group4BeanMethodBasicTests.class);
    }

    @Configuration
    public static class AppConfig {
        @Bean("databaseUrl")
        public String databaseUrl() {
            return "jdbc:postgresql://localhost/test";
        }

        @Bean("maxConnections")
        public Integer maxConnections() {
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

