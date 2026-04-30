//package io.github.youssefrashidy.legacy;
//
//import io.github.classgraph.ClassGraph;
//import io.github.classgraph.ClassInfo;
//import io.github.classgraph.ScanResult;
//import io.github.youssefrashidy.Context.ContextConfig;
//import io.github.youssefrashidy.Exceptions.AmbiguousBeanException;
//import io.github.youssefrashidy.Exceptions.AmbiguousConstructorException;
//import io.github.youssefrashidy.Exceptions.UnregisteredDependencyException;
//import io.github.youssefrashidy.annotations.Component;
//import io.github.youssefrashidy.annotations.Inject;
//
//import java.lang.reflect.Constructor;
//import java.lang.reflect.InvocationTargetException;
//import java.lang.reflect.Parameter;
//import java.util.*;
//
//@Deprecated
//public class LegacyApplicationContext {
//    private final Map<Class<?>, Object> beanRegistry = new HashMap<>();
//    private final Map<Class<?>, List<Class<?>>> resolveMap = new HashMap<>();
//    private final ContextConfig config;
//
//
//    private static final Set<Class<?>> UNRESOLVABLE = Set.of(
//            byte.class, short.class, int.class, long.class,
//            float.class, double.class, boolean.class, char.class,
//            Byte.class, Short.class, Integer.class, Long.class,
//            Float.class, Double.class, Boolean.class, Character.class,
//            String.class  // also unresolvable without @Value equivalent
//    );
//
//    public LegacyApplicationContext(Set<String> paths) {
//        this.config = new ContextConfig(paths);
//        System.out.println("[ApplicationContext] created with base packages: " + config.basePackages());
//        initializeContext();
//    }
//
//    public LegacyApplicationContext(Class<?> entryPoint) {
//        this.config = new ContextConfig(Set.of(entryPoint.getPackageName()));
//        System.out.println("[ApplicationContext] created from entry point: " + entryPoint.getName());
//        initializeContext();
//    }
//
//    private void resolvePackages() {
//        System.out.println("[ApplicationContext] resolvePackages() started for: " + config.basePackages());
//        try (ScanResult scanResult = new ClassGraph()
//                .enableClassInfo()
//                .enableAllInfo()
//                .acceptPackages(config.basePackages().toArray(new String[0]))
//                .scan()) {
//            for (ClassInfo classInfo : scanResult.getAllClasses()) {
//                Class<?> cls = classInfo.loadClass();
//                System.out.println("[ApplicationContext] scanned class: " + cls.getName());
//                if (cls.isAnnotationPresent(Component.class)) {
//                    System.out.println("[ApplicationContext] component found: " + cls.getName());
//                    for (Class<?> abstraction : cls.getInterfaces())
//                        resolveMap.computeIfAbsent(abstraction, _ -> new ArrayList<>())
//                                .add(cls);
//                }
//            }
//        }
//        System.out.println("[ApplicationContext] resolvePackages() finished. mappings=" + resolveMap.keySet());
//    }
//
//    private void initializeContext() {
//        System.out.println("[ApplicationContext] initializeContext() start");
//        resolvePackages();
//        for (var entry : resolveMap.entrySet()) {
//            Class<?> cls = entry.getKey();
//            System.out.println("[ApplicationContext] eager initialize interface key: " + cls.getName());
//            initializeBean(cls);
//        }
//        System.out.println("[ApplicationContext] initializeContext() end");
//    }
//
//    private <T> void initializeBean(Class<T> cls) {
//        System.out.println("[ApplicationContext] initializeBean(" + cls.getName() + ")");
//        if (cls.isInterface()) {
//            var classes = resolveMap.get(cls);
//            System.out.println("[ApplicationContext] resolving interface bean: " + cls.getName() + " -> " + classes);
//            //TODO add exception message
//            //TODO implement qualifiers
//            if (classes.size() > 1) {
//                String candidates = classes.stream().map(Class::getName).sorted().reduce((a, b) -> a + ", " + b).orElse("<none>");
//                throw new AmbiguousBeanException(
//                        "Multiple beans found for interface '" + cls.getName() + "': [" + candidates + "]. " +
//                                "Use @Qualifier to choose a specific implementation."
//                );
//            }
//            else {
//                Class<?> beanCls = classes.getFirst();
//                System.out.println("[ApplicationContext] interface resolved to: " + beanCls.getName());
//                initializeBean(beanCls);
//            }
//        } else {
//            if (beanRegistry.containsKey(cls)) {
//                System.out.println("[ApplicationContext] cache hit: " + cls.getName());
//                return;
//            }
//            Constructor<?>[] constructors = cls.getDeclaredConstructors();
//            System.out.println("[ApplicationContext] constructors found for " + cls.getName() + ": " + constructors.length);
//            // default constructor no need for inject acting as a base case
//            if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
//                Constructor<?> constructor = constructors[0];
//                constructor.setAccessible(true); // To bypass access modifiers
//                if (beanRegistry.containsKey(cls)) {
//                    System.out.println("[ApplicationContext] cache hit after accessibility set: " + cls.getName());
//                    return;
//                } else {
//                    try {
//                        System.out.println("[ApplicationContext] invoking default constructor: " + cls.getName());
//                        T obj = (T) constructor.newInstance();
//                        beanRegistry.put(cls, obj);
//                        System.out.println("[ApplicationContext] bean created: " + cls.getName());
//                        return;
//                    } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
//                        System.out.println("[ApplicationContext] default constructor failed for " + cls.getName() + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
//                        throw new RuntimeException(e);
//                    }
//                }
//            }
//            Constructor<?>[] annotatedCons = Arrays.stream(constructors)
//                    .filter(c -> c.isAnnotationPresent(Inject.class))
//                    .toArray(Constructor[]::new);
//            System.out.println("[ApplicationContext] @Inject constructors for " + cls.getName() + ": " + annotatedCons.length);
//            if (annotatedCons.length > 1)
//                throw new AmbiguousConstructorException("Class " + cls.getName() + " has " + annotatedCons.length + " constructors — exactly one is required. " +
//                        "Annotate the intended constructor with @Inject.");
//            Constructor<?> constructor = annotatedCons[0];
//            Parameter[] parameters = constructor.getParameters();
//            System.out.println("[ApplicationContext] resolving " + parameters.length + " parameters for " + cls.getName());
//            for (var param : parameters) {
//                System.out.println("[ApplicationContext] checking parameter: " + param.getName() + " : " + param.getType().getName());
//                if (UNRESOLVABLE.contains(param.getType()))
//                    throw new UnregisteredDependencyException(
//                            "Parameter '" + param.getName() + "' of type " + param.getType().getName() +
//                                    " in " + cls.getName() + " is a primitive/value type and cannot be injected. " +
//                                    "Use a dedicated configuration object instead."
//                    );
//
//                if (!param.getType().isAnnotationPresent(Component.class) && !resolveMap.containsKey(param.getType()))
//                    throw new UnregisteredDependencyException("Field '" + param.getName() + "' of type " + param.getType().getName() +
//                            " in " + cls.getName() + " is not registered as a bean.");
//            }
//
//            Class<?>[] paramClasses = Arrays.stream(parameters)
//                    .map(Parameter::getType)
//                    .toArray(Class<?>[]::new);
//            ArrayList<Object> beans = new ArrayList<>(paramClasses.length);
//            for (var paramClass : paramClasses) {
//                Class<?> resolvedClass;
//                if (paramClass.isInterface()) {
//                    var implementations = resolveMap.get(paramClass);
//                    System.out.println("[ApplicationContext] interface dependency " + paramClass.getName() + " implementations: " + implementations);
//                    if (implementations.size() > 1) {
//                        String candidates = implementations.stream().map(Class::getName).sorted().reduce((a, b) -> a + ", " + b).orElse("<none>");
//                        throw new AmbiguousBeanException(
//                                "Cannot resolve dependency interface '" + paramClass.getName() + "' while creating '" + cls.getName() +
//                                        "': multiple candidates found [" + candidates + "]. Use @Qualifier to disambiguate."
//                        );
//                    } else if (implementations.isEmpty()) {
//                        throw new UnregisteredDependencyException(
//                                "Cannot resolve dependency interface '" + paramClass.getName() + "' while creating '" + cls.getName() +
//                                        "': no implementation was registered as a bean."
//                        );
//                    }
//                    resolvedClass = implementations.getFirst();
//                } else resolvedClass = paramClass;
//                if (beanRegistry.containsKey(resolvedClass)) {
//                    System.out.println("[ApplicationContext] dependency cache hit: " + resolvedClass.getName());
//                    beans.add(beanRegistry.get(resolvedClass));
//                } else {
//                    System.out.println("[ApplicationContext] dependency miss, initializing: " + resolvedClass.getName());
//                    initializeBean(resolvedClass);
//                    var bean = beanRegistry.get(resolvedClass);
//                    beans.add(bean);
//                }
//            }
//            try {
//                constructor.setAccessible(true);
//                System.out.println("[ApplicationContext] invoking injected constructor: " + cls.getName());
//                T obj = (T) constructor.newInstance(beans.toArray());
//                beanRegistry.put(cls, obj);
//                System.out.println("[ApplicationContext] bean created: " + cls.getName());
//            } catch (RuntimeException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
//                System.out.println("[ApplicationContext] injected constructor failed for " + cls.getName() + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
//                throw new RuntimeException(e);
//            }
//        }
//    }
//
//    public <T> T getInstance(Class<T> cls) {
//        System.out.println("[ApplicationContext] getInstance(" + cls.getName() + ")");
//        if (beanRegistry.containsKey(cls)) {
//            System.out.println("[ApplicationContext] getInstance cache hit: " + cls.getName());
//            return (T) beanRegistry.get(cls);
//        } else if (resolveMap.containsKey(cls)) {
//            System.out.println("[ApplicationContext] getInstance found interface mapping: " + cls.getName());
//            // get the concrete class and return if exist
//            Class<?> resolvedClass;
//            var implementations = resolveMap.get(cls);
//            System.out.println("[ApplicationContext] interface dependency " + cls.getName() + " implementations: " + implementations);
//            if (implementations.size() > 1) {
//                String candidates = implementations.stream().map(Class::getName).sorted().reduce((a, b) -> a + ", " + b).orElse("<none>");
//                throw new AmbiguousBeanException(
//                        "Cannot resolve requested type '" + cls.getName() + "': multiple implementations found [" + candidates + "]. " +
//                                "Use @Qualifier to pick one implementation."
//                );
//            } else if (implementations.isEmpty()) {
//                throw new UnregisteredDependencyException(
//                        "Cannot resolve requested type '" + cls.getName() + "': no implementation was registered as a bean."
//                );
//            }
//            resolvedClass = implementations.getFirst();
//            return (T) beanRegistry.get(resolvedClass);
//        }
//
//        Constructor<?>[] constructors = cls.getDeclaredConstructors();
//        // default constructor no need for inject acting as a base case
//        if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
//            Constructor<?> constructor = constructors[0];
//            constructor.setAccessible(true); // To bypass access modifiers
//            if (beanRegistry.containsKey(cls)) {
//                System.out.println("[ApplicationContext] getInstance cache hit after constructor lookup: " + cls.getName());
//                return (T) beanRegistry.get(cls);
//            } else {
//                try {
//                    System.out.println("[ApplicationContext] getInstance invoking default constructor: " + cls.getName());
//                    T obj = (T) constructor.newInstance();
//                    beanRegistry.put(cls, obj);
//                    System.out.println("[ApplicationContext] getInstance bean created: " + cls.getName());
//                    return obj;
//                } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
//                    System.out.println("[ApplicationContext] getInstance default constructor failed for " + cls.getName() + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
//                    throw new RuntimeException(e);
//                }
//            }
//        }
//        Constructor<?>[] annotatedCons = Arrays.stream(constructors)
//                .filter(c -> c.isAnnotationPresent(Inject.class))
//                .toArray(Constructor[]::new);
//        if (annotatedCons.length > 1)
//            throw new AmbiguousConstructorException("Class " + cls.getName() + " has " + annotatedCons.length + " constructors — exactly one is required. " +
//                    "Annotate the intended constructor with @Inject.");
//        Constructor<?> constructor = annotatedCons[0];
//        Parameter[] parameters = constructor.getParameters();
//        for (var param : parameters) {
//            if (UNRESOLVABLE.contains(param.getType()))
//                throw new UnregisteredDependencyException(
//                        "Parameter '" + param.getName() + "' of type " + param.getType().getName() +
//                                " in " + cls.getName() + " is a primitive/value type and cannot be injected. " +
//                                "Use a dedicated configuration object instead."
//                );
//
//            if (!param.getType().isAnnotationPresent(Component.class) && !resolveMap.containsKey(param.getType()))
//                throw new UnregisteredDependencyException("Field '" + param.getName() + "' of type " + param.getType().getName() +
//                        " in " + cls.getName() + " is not registered as a bean.");
//        }
//
//        Class<?>[] paramClasses = Arrays.stream(parameters)
//                .map(Parameter::getType)
//                .toArray(Class<?>[]::new);
//        ArrayList<Object> beans = new ArrayList<>(paramClasses.length);
//        for (var paramClass : paramClasses) {
//            if (beanRegistry.containsKey(paramClass)) {
//                System.out.println("[ApplicationContext] getInstance dependency cache hit: " + paramClass.getName());
//                beans.add(beanRegistry.get(paramClass));
//            } else {
//                System.out.println("[ApplicationContext] getInstance dependency miss, resolving: " + paramClass.getName());
//                var bean = getInstance(paramClass);
//                beanRegistry.put(paramClass, bean);
//                beans.add(bean);
//            }
//        }
//        try {
//            constructor.setAccessible(true);
//            System.out.println("[ApplicationContext] getInstance invoking injected constructor: " + cls.getName());
//            T obj = (T) constructor.newInstance(beans.toArray());
//            beanRegistry.put(cls, obj);
//            System.out.println("[ApplicationContext] getInstance bean created: " + cls.getName());
//            return obj;
//        } catch (RuntimeException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
//            System.out.println("[ApplicationContext] getInstance injected constructor failed for " + cls.getName() + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
//            throw new RuntimeException(e);
//        }
//    }
//}
