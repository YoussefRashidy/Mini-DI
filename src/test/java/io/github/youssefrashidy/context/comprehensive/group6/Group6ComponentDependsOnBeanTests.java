package io.github.youssefrashidy.context.comprehensive.group6;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.ApplicationContext;
import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Configuration;
import io.github.youssefrashidy.annotations.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 6 — component depending on a @Bean-produced type")
class Group6ComponentDependsOnBeanTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group6ComponentDependsOnBeanTests.class);
    }

    public static class DataSource {
        final String url;
        final int identity = System.identityHashCode(this);

        DataSource(String url) {
            this.url = url;
        }
    }

    @Configuration
    public static class InfraConfig {
        @Bean("dataSource")
        public DataSource dataSource() {
            return new DataSource("jdbc:h2:mem:test");
        }
    }

    @Component
    static class UserRepository {
        final DataSource ds;

        @Inject
        UserRepository(DataSource ds) {
            this.ds = ds;
        }
    }

    @Test
    @DisplayName("B6_T1 — the component depending on a bean-produced type resolves")
    void userRepositoryResolves() {
        ApplicationContext ctx = newContext();

        UserRepository repo = ctx.getInstance(UserRepository.class);
        assertNotNull(repo);
        assertNotNull(repo.ds);
    }

    @Test
    @DisplayName("B6_T2 — the injected DataSource is the same singleton as the direct lookup")
    void injectedDataSourceMatchesDirectLookup() {
        ApplicationContext ctx = newContext();

        UserRepository repo = ctx.getInstance(UserRepository.class);
        DataSource ds = ctx.getInstance(DataSource.class);

        assertSame(ds, repo.ds);
    }

    @Test
    @DisplayName("B6_T3 — the injected DataSource carries the expected URL")
    void injectedDataSourceHasExpectedUrl() {
        ApplicationContext ctx = newContext();

        UserRepository repo = ctx.getInstance(UserRepository.class);
        assertEquals("jdbc:h2:mem:test", repo.ds.url);
    }
}

