package io.github.youssefrashidy.Context.refactor;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.github.youssefrashidy.Context.ContextConfig;
import io.github.youssefrashidy.annotations.Component;

import java.util.*;

public class ComponentScanner {


    private ScanMap resolvePackages(Set<String> basePackages) {
        final Map<Class<?>, List<Class<?>>> resolveMap = new HashMap<>();
        final List<Class<?>> componentList = new ArrayList<>();

        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .enableAllInfo()
                .acceptPackages(basePackages.toArray(new String[0]))
                .scan()) {
            for (ClassInfo classInfo : scanResult.getAllClasses()) {
                Class<?> cls = classInfo.loadClass();
                if (cls.isAnnotationPresent(Component.class)) {
                    componentList.add(cls);
                    for (Class<?> abstraction : cls.getInterfaces())
                        resolveMap.computeIfAbsent(abstraction, _ -> new ArrayList<>())
                                .add(cls);
                }
            }
        }
        return new ScanMap(resolveMap,componentList) ;
    }
}
