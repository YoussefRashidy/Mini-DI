package io.github.youssefrashidy.Context;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.github.youssefrashidy.Exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.Exceptions.AmbiguousConstructorException;
import io.github.youssefrashidy.Exceptions.CircularDependencyException;
import io.github.youssefrashidy.Exceptions.DuplicateBeanIdentifierException;
import io.github.youssefrashidy.Exceptions.UnregisteredDependencyException;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.annotations.Qualifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ApplicationContextRef {
    //    private final Map<BeanDefinition, Supplier<?>> beanRegistry = new HashMap<>();
    private final Map<String, Supplier<?>> beanRegistry = new HashMap<>();   // source of truth
    private final Map<String, BeanDefinition> definitions = new HashMap<>();  // metadata
    private final Map<Class<?>, List<String>> typeIndex = new HashMap<>();    // type -> identifiers

    private final Map<Class<?>, List<Class<?>>> resolveMap = new HashMap<>();
    private final List<Class<?>> componentList = new ArrayList<>();
    private final ContextConfig config;

    private static final Set<Class<?>> UNRESOLVABLE = Set.of(
            byte.class, short.class, int.class, long.class,
            float.class, double.class, boolean.class, char.class,
            Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, Boolean.class, Character.class,
            String.class  // also unresolvable without @Value equivalent
    );

    public ApplicationContextRef(Set<String> paths) {
        this.config = new ContextConfig(paths);
        initializeContext();
    }

    public ApplicationContextRef(Class<?> entryPoint) {
        this.config = new ContextConfig(Set.of(entryPoint.getPackageName()));
        initializeContext();
    }


    private void initializeContext() {
        resolvePackages();
        Map<Class<?>, Set<Class<?>>> classGraph = new HashMap<>();
        Map<Class<?>, Integer> indegreeMap = new HashMap<>();
        buildMaps(classGraph, indegreeMap);
        var initOrder = topologicalSort(classGraph, indegreeMap);
        instantiateBeans(initOrder);

    }

    public <T> T getInstance(Class<T> cls) {
        List<String> identifiers = typeIndex.getOrDefault(cls, Collections.emptyList());
        if (identifiers.isEmpty()) {
            throw new UnregisteredDependencyException(
                    "DI error: no bean registered for requested type '" + cls.getName() + "'."
            );
        }

        if (identifiers.size() > 1) {
            String candidates = identifiers.stream()
                    .map(id -> definitions.get(id).cls().getName() + " as '" + id + "'")
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new AmbiguousBeanException(
                    "DI error: multiple beans registered for requested type '" + cls.getName() + "': [" + candidates +
                            "]. Use getInstance(String, Class) with a qualifier identifier."
            );
        }

        return getInstance(identifiers.getFirst(), cls);
    }

    public Object getInstance(String identifier) {
        Supplier<?> supplier = beanRegistry.get(identifier);
        if (supplier == null) {
            throw new UnregisteredDependencyException(
                    "DI error: no bean registered with identifier '" + identifier + "'."
            );
        }
        return supplier.get();
    }

    public <T> T getInstance(String identifier, Class<T> expectedType) {
        Object bean = getInstance(identifier);
        if (!expectedType.isInstance(bean)) {
            throw new UnregisteredDependencyException(
                    "DI error: bean identifier '" + identifier + "' resolves to type '" + bean.getClass().getName() +
                            "', not assignable to requested type '" + expectedType.getName() + "'."
            );
        }
        return expectedType.cast(bean);
    }

    public Set<String> getBeanIdentifiers() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    private void instantiateBeans(List<Class<?>> initOrder) {
        for (var cls : initOrder) {
            Constructor<?>[] constructors = cls.getDeclaredConstructors();
            // check default Constructor
            if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
                String identifier = resolveIdentifier(cls);
                Constructor<?> cons = constructors[0];
                cons.setAccessible(true);
                try {
                    var instance = cons.newInstance(new Object[0]);
                    Supplier<?> supplier = () -> instance;
                    registerBean(cls, identifier, supplier);

                } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // This is valid because of the way the graph is built
                var annotatedConstructor = Arrays.stream(constructors)
                        .filter(c -> c.isAnnotationPresent(Inject.class))
                        .toArray(Constructor<?>[]::new)[0];
                annotatedConstructor.setAccessible(true);

                Parameter[] params = annotatedConstructor.getParameters();
                ArrayList<Object> beans = new ArrayList<>(params.length);

                for (var param : params) {
                    if (param.getType().isInterface()) {
                        // if annotated get by annotation
                        String identifier;
                        if (param.isAnnotationPresent(Qualifier.class)) {
                            identifier = param.getAnnotation(Qualifier.class).value();
                            var paramInstance = beanRegistry.get(identifier).get();
                            beans.add(paramInstance);
                        } else {
                            var paramCls = resolveMap.get(param.getType()).getFirst();
                            identifier = resolveIdentifier(paramCls);
                            Object paramInstance = beanRegistry.get(identifier).get();
                            beans.add(paramInstance);
                        }
                    } else {
                        String identifier = resolveIdentifier(param.getType());
                        beans.add(beanRegistry.get(identifier).get());
                    }
                }
                try {
                    Object instance = annotatedConstructor.newInstance(beans.toArray());
                    String identifier = resolveIdentifier(cls);
                    Supplier<?> supplier = () -> instance;
                    registerBean(cls, identifier, supplier);
                } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

            }
        }
    }

    private void resolvePackages() {
        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .enableAllInfo()
                .acceptPackages(config.basePackages().toArray(new String[0]))
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
    }

    private void buildMaps(Map<Class<?>, Set<Class<?>>> classGraph, Map<Class<?>, Integer> indegreeMap) {
        for (var cls : componentList) {
            /*
             * Get constructor
             * Build adjacency list
             * Build inDegree map
             */
            Constructor<?>[] constructors = cls.getDeclaredConstructors();
            // check default Constructor
            if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
                classGraph.put(cls, Collections.emptySet());
                indegreeMap.put(cls, 0);
                continue;
            }
            // get annotated Constructor
            var annotatedConstructors = Arrays.stream(constructors)
                    .filter(c -> c.isAnnotationPresent(Inject.class))
                    .toArray(Constructor<?>[]::new);
            if (annotatedConstructors.length > 1)
                throw new AmbiguousConstructorException("Class " + cls.getName() + " has " + annotatedConstructors.length + " constructors — exactly one is required. " +
                        "Annotate the intended constructor with @Inject.");

            var constructor = annotatedConstructors[0];
            Parameter[] params = constructor.getParameters();

            for (var param : params) {
                if (UNRESOLVABLE.contains(param.getType()))
                    throw new UnregisteredDependencyException(
                            "DI error: cannot inject parameter '" + param.getName() + "' of type '" + param.getType().getName() +
                                    "' in bean '" + cls.getName() + "' because primitive/value types are not supported. " +
                                    "Use a dedicated configuration bean instead."
                    );

                if (!param.getType().isAnnotationPresent(Component.class) && !resolveMap.containsKey(param.getType()))
                    throw new UnregisteredDependencyException(
                            "DI error: missing bean for parameter '" + param.getName() + "' of type '" + param.getType().getName() +
                                    "' required by bean '" + cls.getName() + "'."
                    );
                // handle interfaces
                var candidateClass = param.getType().isInterface() ? resolveParamType(param) : param.getType();
                classGraph.computeIfAbsent(cls, _ -> new HashSet<Class<?>>()).add(candidateClass);

            }
            indegreeMap.put(cls, classGraph.get(cls).size());
        }
    }

    private Class<?> resolveParamType(Parameter param) {
        var classes = resolveMap.get(param.getType());

        if (classes.size() > 1) {
            // Look for qualifier over the field
            if (param.isAnnotationPresent(Qualifier.class)) {
                String val = param.getAnnotation(Qualifier.class).value();
                var candidates = resolveMap.get(param.getType()).stream()
                        .filter(cls -> cls.isAnnotationPresent(Qualifier.class) && cls.getAnnotation(Qualifier.class).value().equals(val))
                        .toList();
                if (candidates.size() > 1)
                    throw new AmbiguousBeanException(
                            "DI error: multiple beans match qualifier '" + val + "' for interface '" + param.getType().getName() +
                                    "' on parameter '" + param.getName() + "': [" +
                                    candidates.stream().map(Class::getName).sorted().collect(Collectors.joining(", ")) + "]."
                    );
                if (candidates.isEmpty())
                    throw new AmbiguousBeanException(
                            "DI error: no bean matches qualifier '" + val + "' for interface '" + param.getType().getName() +
                                    "' on parameter '" + param.getName() + "'."
                    );

                return candidates.getFirst();
            }
            else {
                String candidates = classes.stream().map(Class::getName).sorted().collect(Collectors.joining(", "));
                throw new AmbiguousBeanException(
                        "DI error: multiple beans found for interface '" + param.getType().getName() + "' on parameter '" +
                                param.getName() + "': [" + candidates + "]. Add @Qualifier to disambiguate."
                );
            }
        }
        else if (classes.size() == 1)
            return classes.getFirst();
        else
            throw new UnregisteredDependencyException(
                    "DI error: no bean implementation registered for interface '" + param.getType().getName() +
                            "' required by parameter '" + param.getName() + "'."
            );
    }

    private List<Class<?>> topologicalSort(Map<Class<?>, Set<Class<?>>> classGraph, Map<Class<?>, Integer> indegreeMap) {
        Deque<Class<?>> zeroDegreeClasses = indegreeMap.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayDeque::new));

        List<Class<?>> initializationOrder = new ArrayList<>();

        while (!zeroDegreeClasses.isEmpty()) {
            var cls = zeroDegreeClasses.poll();
            initializationOrder.add(cls);

            Set<Class<?>> dependentClasses = classGraph.entrySet().stream()
                    .filter(entry -> entry.getValue().contains(cls))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            indegreeMap.entrySet().stream()
                    .filter(entry -> dependentClasses.contains(entry.getKey()))
                    .peek(entry -> entry.setValue(entry.getValue() - 1))
                    .filter(entry -> entry.getValue() == 0)
                    .forEach(entry -> zeroDegreeClasses.push(entry.getKey()));
        }

        if (initializationOrder.size() < classGraph.size()) {
            String unresolved = indegreeMap.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(entry -> entry.getKey().getName())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new CircularDependencyException(
                    "DI error: circular dependency detected among beans: [" + unresolved + "]. " +
                            "Review constructor dependencies to break the cycle."
            );
        }

        return initializationOrder;
    }

    private String resolveIdentifier(Class<?> cls) {
        return cls.isAnnotationPresent(Qualifier.class)
                ? cls.getAnnotation(Qualifier.class).value()
                : cls.getSimpleName();
    }

    private void registerBean(Class<?> cls, String identifier, Supplier<?> supplier) {
        BeanDefinition existing = definitions.get(identifier);
        if (existing != null && !existing.cls().equals(cls)) {
            throw new DuplicateBeanIdentifierException(
                    "DI error: duplicate bean identifier '" + identifier + "' for beans '" + existing.cls().getName() +
                            "' and '" + cls.getName() + "'. Identifiers must be unique."
            );
        }

        beanRegistry.put(identifier, supplier);
        definitions.put(identifier, new BeanDefinition(cls, identifier));
        addTypeMapping(cls, identifier);
        for (Class<?> abstraction : cls.getInterfaces()) {
            addTypeMapping(abstraction, identifier);
        }
    }

    private void addTypeMapping(Class<?> type, String identifier) {
        List<String> identifiers = typeIndex.computeIfAbsent(type, _ -> new ArrayList<>());
        if (!identifiers.contains(identifier)) {
            identifiers.add(identifier);
        }
    }
}
