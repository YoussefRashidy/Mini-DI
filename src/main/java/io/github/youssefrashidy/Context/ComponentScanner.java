package io.github.youssefrashidy.Context;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Configuration;

import java.util.*;

public class ComponentScanner {

    public ScanMap scan(ContextConfig config) {
        return resolvePackages(config.basePackages());
    }

    private ScanMap resolvePackages(Set<String> basePackages) {
        final Map<Class<?>, List<Class<?>>> resolveMap = new HashMap<>();
        final List<Class<?>> componentList = new ArrayList<>();
        final List<Class<?>> configurationClasses = new ArrayList<>() ;

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
                else if (cls.isAnnotationPresent(Component.class)) {
                    componentList.add(cls);
                    for (Class<?> abstraction : cls.getInterfaces())
                        resolveMap.computeIfAbsent(abstraction, _ -> new ArrayList<>())
                                .add(cls);
                }

            }
        }
        return new ScanMap(resolveMap, componentList , configurationClasses);
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
