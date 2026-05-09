package io.github.youssefrashidy.context.miniProject.context;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.miniProject.fixtures.config.broken.duplicate.DuplicateBeanScanAnchor;
import io.github.youssefrashidy.context.miniProject.fixtures.config.broken.missing.MissingDependencyScanAnchor;
import io.github.youssefrashidy.exceptions.DuplicateBeanIdentifierException;
import io.github.youssefrashidy.exceptions.UnregisteredDependencyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Configuration exception paths")
public class ConfigurationExceptionPathsTest {

    @Test
    @DisplayName("Missing @Bean dependency fails fast with UnregisteredDependencyException")
    void missingBeanDependencyThrows() {
        UnregisteredDependencyException ex = assertThrows(
                UnregisteredDependencyException.class,
                () -> new AnnotationConfigApplicationContext(MissingDependencyScanAnchor.class)
        );

        assertTrue(ex.getMessage().contains("configuration bean method"));
        assertTrue(ex.getMessage().contains("missingBean") || ex.getMessage().contains("missingConfigBean"));
    }

    @Test
    @DisplayName("Duplicate @Bean identifiers fail context initialization")
    void duplicateBeanIdentifiersThrow() {
        DuplicateBeanIdentifierException ex = assertThrows(
                DuplicateBeanIdentifierException.class,
                () -> new AnnotationConfigApplicationContext(DuplicateBeanScanAnchor.class)
        );

        assertTrue(ex.getMessage().contains("duplicate bean identifier"));
    }
}

