package io.github.youssefrashidy.Context.comprehensive.group9;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Configuration;
import io.github.youssefrashidy.annotations.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 9 — @Bean returning a subtype injected as a supertype")
class Group9SubtypeBeanTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group9SubtypeBeanTests.class);
    }

    static abstract class Cache {
        abstract int size();
    }

    static class InMemoryCache extends Cache {
        @Override
        int size() {
            return 100;
        }
    }

    @Configuration
    static class CacheConfig {
        @Bean("cache")
        InMemoryCache cache() {
            return new InMemoryCache();
        }
    }

    @Component
    static class CacheConsumer {
        final Cache cache;

        @Inject
        CacheConsumer(Cache cache) {
            this.cache = cache;
        }
    }

    @Test
    @DisplayName("C9_T1 — concrete subtype lookup resolves to the @Bean-produced instance")
    void concreteSubtypeLookupResolves() {
        ApplicationContext ctx = newContext();

        InMemoryCache cache = ctx.getInstance(InMemoryCache.class);
        assertNotNull(cache);
        assertEquals(100, cache.size());
    }

    @Test
    @DisplayName("C9_T2 — supertype lookup resolves to the subtype instance")
    void supertypeLookupResolvesToSubtype() {
        ApplicationContext ctx = newContext();

        Cache cache = ctx.getInstance(Cache.class);
        assertInstanceOf(InMemoryCache.class, cache);
        assertEquals(100, cache.size());
    }

    @Test
    @DisplayName("C9_T3 — constructor injection by declared supertype works for a bean-produced subtype")
    void constructorInjectionByDeclaredSupertypeWorks() {
        ApplicationContext ctx = newContext();

        CacheConsumer consumer = ctx.getInstance(CacheConsumer.class);
        Cache cache = ctx.getInstance(Cache.class);

        assertNotNull(consumer.cache);
        assertSame(cache, consumer.cache);
    }
}

