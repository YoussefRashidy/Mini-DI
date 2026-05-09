package io.github.youssefrashidy.context.comprehensive.group5;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.ApplicationContext;
import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 5 — @Bean methods taking @Component parameters")
public class Group5BeanParameterTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group5BeanParameterTests.class);
    }

    public static class ConnectionPool {
        final int size;
        final int identity = System.identityHashCode(this);

        ConnectionPool(int size) {
            this.size = size;
        }
    }

    @Configuration
    public static class AppConfig {
        @Bean("connectionPool")
        public ConnectionPool connectionPool() {
            return new ConnectionPool(5);
        }

        @Bean("connectionStats")
        public String connectionStats(ConnectionPool pool) {
            return "pool-size=" + pool.size + ";pool-id=" + pool.identity;
        }
    }

    @Test
    @DisplayName("B5_T1 — the component-backed bean resolves correctly")
    void connectionPoolResolves() {
        ApplicationContext ctx = newContext();

        ConnectionPool pool = ctx.getInstance(ConnectionPool.class);
        assertNotNull(pool);
        assertEquals(5, pool.size);
    }

    @Test
    @DisplayName("B5_T2 — the @Bean method sees the injected ConnectionPool singleton")
    void beanMethodReceivesComponentDependency() {
        ApplicationContext ctx = newContext();

        ConnectionPool pool = ctx.getInstance(ConnectionPool.class);
        String stats = ctx.getInstance(String.class);

        assertEquals("pool-size=5", stats.substring(0, "pool-size=5".length()));
        assertTrue(stats.contains("pool-id=" + pool.identity));
    }

    @Test
    @DisplayName("B5_T3 — the same ConnectionPool singleton is reused everywhere")
    void connectionPoolIsSingletonAcrossLookups() {
        ApplicationContext ctx = newContext();

        ConnectionPool first = ctx.getInstance(ConnectionPool.class);
        ConnectionPool second = ctx.getInstance(ConnectionPool.class);
        String stats = ctx.getInstance(String.class);

        assertSame(first, second);
        assertTrue(stats.contains("pool-id=" + first.identity));
    }
}

