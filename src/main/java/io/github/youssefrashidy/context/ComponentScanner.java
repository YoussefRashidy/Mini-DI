package io.github.youssefrashidy.context;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Configuration;
import io.github.youssefrashidy.annotations.ScopeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ComponentScanner {
    private static final Logger logger = LoggerFactory.getLogger(ComponentScanner.class);

    public ScanMap scan(ContextConfig config) {
        logger.info("Starting component scan for base packages: {}", config.basePackages());
        var result = resolvePackages(config.basePackages());
        logger.info("Component scan completed: {} components, {} configuration classes",
                result.components().size(), result.configurationClasses().size());
        return result;
    }

    private ScanMap resolvePackages(Set<String> basePackages) {
        final Map<Class<?>, List<ComponentBeanDefinition>> resolveMap = new HashMap<>();
        final List<ComponentBeanDefinition> components = new ArrayList<>();
        final List<Class<?>> configurationClasses = new ArrayList<>();
        DependencyResolver resolver = new DependencyResolver();

        logger.debug("Resolving packages: {}", basePackages);
        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .enableAllInfo()
                .acceptPackages(basePackages.toArray(new String[0]))
                .scan()) {
            logger.debug("ClassGraph found {} classes", scanResult.getAllClasses().size());
            for (ClassInfo classInfo : scanResult.getAllClasses()) {
                Class<?> cls = classInfo.loadClass();
                logger.trace("Inspecting class: {}", cls.getName());
                if (isConfiguration(cls)) {
                    logger.debug("Found @Configuration class: {}", cls.getName());
                    configurationClasses.add(cls);
                } else if (isComponent(cls)) {
                    String identifier = resolver.resolveIdentifier(cls);
                    var definition = new ComponentBeanDefinition(cls, resolveScope(cls), identifier);
                    logger.debug("Found @Component: {} (identifier={}, scope={})",
                            cls.getName(), identifier, definition.scope());
                    components.add(definition);
                    for (Class<?> abstraction : cls.getInterfaces()) {
                        logger.trace("Mapping component {} -> interface {}", cls.getName(), abstraction.getName());
                        resolveMap.computeIfAbsent(abstraction, _ -> new ArrayList<>())
                                .add(definition);
                    }
                }

            }
        }
        if (components.isEmpty()) logger.warn("No components found in packages: {}", basePackages);
        return new ScanMap(resolveMap, components, configurationClasses);
    }

    private ScopeType resolveScope(Class<?> cls) {
        if (cls.isAnnotationPresent(io.github.youssefrashidy.annotations.Scope.class)) {
            return cls.getAnnotation(io.github.youssefrashidy.annotations.Scope.class).value();
        }
        for (var annotation : cls.getAnnotations()) {
            var type = annotation.annotationType();
            if (type.isAnnotationPresent(io.github.youssefrashidy.annotations.Scope.class)) {
                return type.getAnnotation(io.github.youssefrashidy.annotations.Scope.class).value();
            }
        }
        return io.github.youssefrashidy.annotations.ScopeType.SINGLETON;
    }

    boolean isComponent(Class<?> cls) {
        if (cls.isAnnotationPresent(Component.class)) return true;
        for (var annotation : cls.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(Component.class)) return true;
        }
        return false;
    }

    boolean isConfiguration(Class<?> cls) {
        if (cls.isAnnotationPresent(Configuration.class)) return true;
        for (var annotation : cls.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(Configuration.class)) return true;
        }
        return false;
    }

}
