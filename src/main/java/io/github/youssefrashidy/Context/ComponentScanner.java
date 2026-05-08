package io.github.youssefrashidy.Context;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Configuration;
import io.github.youssefrashidy.annotations.ScopeType;

import java.util.*;

public class ComponentScanner {

    public ScanMap scan(ContextConfig config) {
        return resolvePackages(config.basePackages());
    }

    private ScanMap resolvePackages(Set<String> basePackages) {
        final Map<Class<?>, List<ComponentBeanDefinition>> resolveMap = new HashMap<>();
        final List<ComponentBeanDefinition> components = new ArrayList<>();
        final List<Class<?>> configurationClasses = new ArrayList<>() ;
        DependencyResolver resolver = new DependencyResolver();

        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .enableAllInfo()
                .acceptPackages(basePackages.toArray(new String[0]))
                .scan()) {
            for (ClassInfo classInfo : scanResult.getAllClasses()) {
                Class<?> cls = classInfo.loadClass();
                if (isConfiguration(cls)) {
                    configurationClasses.add(cls) ;
                }
                else if (isComponent(cls)) {
                    String identifier = resolver.resolveIdentifier(cls);
                    var definition = new ComponentBeanDefinition(cls, resolveScope(cls), identifier);
                    components.add(definition);
                    for (Class<?> abstraction : cls.getInterfaces())
                        resolveMap.computeIfAbsent(abstraction, _ -> new ArrayList<>())
                                .add(definition);
                }

            }
        }
        return new ScanMap(resolveMap, components , configurationClasses);
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
        return io.github.youssefrashidy.annotations.ScopeType.SINGELTON;
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
