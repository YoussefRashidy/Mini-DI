package io.github.youssefrashidy.Context;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.github.youssefrashidy.Exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.Exceptions.AmbiguousConstructorException;
import io.github.youssefrashidy.Exceptions.CircularDependencyException;
import io.github.youssefrashidy.Exceptions.DuplicateBeanIdentifierException;
import io.github.youssefrashidy.Exceptions.UnregisteredDependencyException;
import io.github.youssefrashidy.annotations.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ApplicationContext {
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

    public ApplicationContext(Set<String> paths) {
        this.config = new ContextConfig(paths);
        initializeContext();
    }

    public ApplicationContext(Class<?> entryPoint) {
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
                Constructor<?> cons = constructors[0];
                cons.setAccessible(true);
                resolveScope(cls, cons, new Parameter[0]);
            } else {
                // This is valid because of the way the graph is built
                var annotatedConstructor = Arrays.stream(constructors)
                        .filter(c -> c.isAnnotationPresent(Inject.class))
                        .toArray(Constructor<?>[]::new)[0];
                annotatedConstructor.setAccessible(true);

                Parameter[] params = annotatedConstructor.getParameters();
                ArrayList<Object> beans = new ArrayList<>(params.length);

//                for (var param : params) {
//                    resolveParameter(param, beans);
//                }
                resolveScope(cls, annotatedConstructor, params);
            }
        }
    }

    private void resolveScope(Class<?> cls, Constructor<?> annotatedConstructor, Parameter[] params) {
        try {
            String identifier = resolveIdentifier(cls);
            if (cls.isAnnotationPresent(Scope.class) && cls.getAnnotation(Scope.class).value().equals(ScopeType.PROTOTYPE)) {

                /*
                 * fix resolve the types dynamically to avoid singelton prototypes inside prototype
                 */
                Supplier<?> supplier = () -> {
                    try {
                        ArrayList<Object> beans = new ArrayList<>();
                        for (var para : params) {
                            resolveParameter(para, beans);
                        }
                        return annotatedConstructor.newInstance(beans.toArray());
                    } catch (InstantiationException | InvocationTargetException |
                             IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                };
                registerBean(cls, identifier, supplier);
            } else {
                ArrayList<Object> beans = new ArrayList<>();
                for (Parameter parameter : params) resolveParameter(parameter, beans);
                Object instance = annotatedConstructor.newInstance(beans.toArray());
                Supplier<?> supplier = () -> instance;
                registerBean(cls, identifier, supplier);
            }
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void resolveParameter(Parameter param, ArrayList<Object> beans) {
        var type = param.getType();
        boolean isSupplier = param.getType() == Supplier.class;
        if (isSupplier) {
            Type generic = param.getParameterizedType();

            if (!(generic instanceof ParameterizedType))
                throw new UnregisteredDependencyException(
                        "DI error: Supplier parameter '" + param.getName() + "' must have a type argument e.g. Supplier<Foo>."
                );

            type = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
            type = resolveParamType(param,type) ;
            if (!type.isAnnotationPresent(Scope.class) || type.getAnnotation(Scope.class).value() != ScopeType.PROTOTYPE)
                throw new UnregisteredDependencyException(
                        "DI error: Supplier<" + type.getSimpleName() + "> wraps a non-prototype bean. " +
                                "Only prototype beans may be injected as Supplier."
                );
        }
        if (type.isInterface()) {
            // if annotated get by annotation
            String identifier;
            if (param.isAnnotationPresent(Qualifier.class)) {
                identifier = param.getAnnotation(Qualifier.class).value();
                var paramInstance = isSupplier ? (Supplier<?>) () -> beanRegistry.get(identifier).get() : beanRegistry.get(identifier).get();
                beans.add(paramInstance);
            } else {
                var paramCls = resolveMap.get(type).getFirst();
                identifier = resolveIdentifier(paramCls);
                Object paramInstance = isSupplier ? (Supplier<?>) () -> beanRegistry.get(identifier).get() : beanRegistry.get(identifier).get();
                beans.add(paramInstance);
            }
        } else {
            String identifier = resolveIdentifier(type);
            beans.add(isSupplier ? (Supplier<?>) () -> beanRegistry.get(identifier).get() : beanRegistry.get(identifier).get());
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
                Class<?> type = param.getType();
                if (type == Supplier.class) {
                    Type generic = param.getParameterizedType();
                    if (!(generic instanceof ParameterizedType)) {
                        throw new UnregisteredDependencyException(
                                "DI error: Supplier parameter '" + param.getName() + "' must have a type argument e.g. Supplier<Foo>."
                        );
                    }
                    type = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
                }

                if (UNRESOLVABLE.contains(type))
                    throw new UnregisteredDependencyException(
                            "DI error: cannot inject parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' in bean '" + cls.getName() + "' because primitive/value types are not supported. " +
                                    "Use a dedicated configuration bean instead."
                    );

                if (!type.isAnnotationPresent(Component.class) && !resolveMap.containsKey(type))
                    throw new UnregisteredDependencyException(
                            "DI error: missing bean for parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' required by bean '" + cls.getName() + "'."
                    );
                // handle interfaces
                var candidateClass = resolveParamType(param);
                classGraph.computeIfAbsent(cls, _ -> new HashSet<Class<?>>()).add(candidateClass);

            }
            indegreeMap.put(cls, classGraph.get(cls).size());
        }
    }

    private Class<?> resolveParamType(Parameter param) {
        var type = param.getType();

        if (type == Supplier.class) {
            Type generic = param.getParameterizedType();

            if (!(generic instanceof ParameterizedType))
                throw new UnregisteredDependencyException(
                        "DI error: Supplier parameter '" + param.getName() + "' must have a type argument e.g. Supplier<Foo>."
                );

            type = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
            type = resolveParamType(param,type);
            if (!type.isAnnotationPresent(Scope.class) || type.getAnnotation(Scope.class).value() != ScopeType.PROTOTYPE)
                throw new UnregisteredDependencyException(
                        "DI error: Supplier<" + type.getSimpleName() + "> wraps a non-prototype bean. " +
                                "Only prototype beans may be injected as Supplier."
                );
        }
        return resolveParamType(param, type);
    }

    private Class<?> resolveParamType(Parameter param, Class<?> type) {
        if (!type.isInterface())
            return type;
        var classes = resolveMap.get(type);

        if (classes.size() > 1) {
            // Look for qualifier over the field
            if (param.isAnnotationPresent(Qualifier.class)) {
                String val = param.getAnnotation(Qualifier.class).value();
                var candidates = resolveMap.get(type).stream()
                        .filter(cls -> cls.isAnnotationPresent(Qualifier.class) && cls.getAnnotation(Qualifier.class).value().equals(val))
                        .toList();
                if (candidates.size() > 1)
                    throw new AmbiguousBeanException(
                            "DI error: multiple beans match qualifier '" + val + "' for interface '" + type.getName() +
                                    "' on parameter '" + param.getName() + "': [" +
                                    candidates.stream().map(Class::getName).sorted().collect(Collectors.joining(", ")) + "]."
                    );
                if (candidates.isEmpty())
                    throw new AmbiguousBeanException(
                            "DI error: no bean matches qualifier '" + val + "' for interface '" + type.getName() +
                                    "' on parameter '" + param.getName() + "'."
                    );

                return candidates.getFirst();
            } else {
                String candidates = classes.stream().map(Class::getName).sorted().collect(Collectors.joining(", "));
                throw new AmbiguousBeanException(
                        "DI error: multiple beans found for interface '" + type.getName() + "' on parameter '" +
                                param.getName() + "': [" + candidates + "]. Add @Qualifier to disambiguate."
                );
            }
        } else if (classes.size() == 1)
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
