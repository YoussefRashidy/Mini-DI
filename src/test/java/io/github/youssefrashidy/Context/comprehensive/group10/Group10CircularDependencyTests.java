package io.github.youssefrashidy.Context.comprehensive.group10;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.Exceptions.CircularDependencyException;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 10 — circular dependency detection")
class Group10CircularDependencyTests {

    @Component
    static class Alpha {
        @Inject
        Alpha(Beta b) {
        }
    }

    @Component
    static class Beta {
        @Inject
        Beta(Alpha a) {
        }
    }

    @Test
    @DisplayName("X10_T1 — context initialization fails fast on circular dependencies")
    void initializationFailsFastOnCircularDependency() {
        assertThrows(CircularDependencyException.class, () -> new AnnotationConfigApplicationContext(Group10CircularDependencyTests.class));
    }

}

