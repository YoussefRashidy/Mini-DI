package io.github.youssefrashidy.Context;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.github.youssefrashidy.Exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.Exceptions.AmbiguousConstructorException;
import io.github.youssefrashidy.Exceptions.UnregisteredDependencyException;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.annotations.Singelton;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;

public class ApplicationContext {
    private final Map<Class<?>, Object> beanRegistry = new HashMap<>();
    private final Map<Class<?>, List<Class<?>>> resolveMap = new HashMap<>();
    private final ContextConfig config;


    private static final Set<Class<?>> UNRESOLVABLE = Set.of(
            byte.class, short.class, int.class, long.class,
            float.class, double.class, boolean.class, char.class,
            Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, Boolean.class, Character.class,
            String.class  // also unresolvable without @Value equivalent
    );

    public ApplicationContext(Set<String> paths) {
        this.config = new ContextConfig(paths);
    }

    public ApplicationContext(Class<?> entryPoint) {
        this.config = new ContextConfig(Set.of(entryPoint.getPackageName()));
    }

    private void resolvePackages() {
        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .acceptClasses(config.basePackages().toArray(new String[0]))
                .scan()) {
            for (ClassInfo classInfo : scanResult.getAllClasses()) {
                Class<?> cls = classInfo.loadClass();
                if (cls.isAnnotationPresent(Component.class)) {
                    for (Class<?> abstraction : cls.getInterfaces())
                        resolveMap.computeIfAbsent(abstraction, _ -> new ArrayList<>())
                                .add(cls);
                }
            }
        }
    }

    private void initializeContext() {
        resolvePackages();
        for (var entry : resolveMap.entrySet()) {
            Class<?> cls = entry.getKey();
            if (cls.isInterface()) {
                var classes = entry.getValue();
                //TODO add exception message
                //TODO implement qualifiers
                if (classes.size() > 1) throw new AmbiguousBeanException();
                else {
                    Class<?> beanCls = classes.getFirst();

                }
            }
        }
    }

    private <T> T initializeBean(Class<T> cls) {
        if (cls.isInterface()) {
            var classes = resolveMap.get(cls);
            //TODO add exception message
            //TODO implement qualifiers
            if (classes.size() > 1) throw new AmbiguousBeanException();
            else {
                Class<?> beanCls = classes.getFirst();
                initializeBean(beanCls);
            }
        } else
    }

    public <T> T getInstance(Class<T> cls) {
        Constructor<?>[] constructors = cls.getDeclaredConstructors();
        // default constructor no need for inject acting as a base case
        if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
            Constructor<?> constructor = constructors[0];
            constructor.setAccessible(true); // To bypass access modifiers
            if (beanRegistry.containsKey(cls)) return (T) beanRegistry.get(cls);
            else {
                try {
                    T obj = (T) constructor.newInstance();
                    beanRegistry.put(cls, obj);
                    return obj;
                } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        Constructor<?>[] annotatedCons = Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .toArray(Constructor[]::new);
        if (annotatedCons.length > 1)
            throw new AmbiguousConstructorException("Class " + cls.getName() + " has " + annotatedCons.length + " constructors — exactly one is required. " +
                    "Annotate the intended constructor with @Inject.");
        Constructor<?> constructor = annotatedCons[0];
        Parameter[] parameters = constructor.getParameters();
        for (var param : parameters) {
            if (UNRESOLVABLE.contains(param.getType()))
                throw new UnregisteredDependencyException(
                        "Parameter '" + param.getName() + "' of type " + param.getType().getName() +
                                " in " + cls.getName() + " is a primitive/value type and cannot be injected. " +
                                "Use a dedicated configuration object instead."
                );

            if (!param.getType().isAnnotationPresent(Singelton.class))
                throw new UnregisteredDependencyException("Field '" + param.getName() + "' of type " + param.getType().getName() +
                        " in " + cls.getName() + " is not registered as a bean.");
        }

        Class<?>[] paramClasses = Arrays.stream(parameters)
                .map(Parameter::getType)
                .toArray(Class<?>[]::new);
        ArrayList<Object> beans = new ArrayList<>(paramClasses.length);
        for (var paramClass : paramClasses) {
            if (beanRegistry.containsKey(paramClass)) {
                beans.add(beanRegistry.get(paramClass));
            } else {
                var bean = getInstance(paramClass);
                beanRegistry.put(paramClass, bean);
                beans.add(bean);
            }
        }
        try {
            constructor.setAccessible(true);
            T obj = (T) constructor.newInstance(beans.toArray());
            return obj;
        } catch (RuntimeException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
