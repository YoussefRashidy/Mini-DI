package io.github.youssefrashidy.context;

import io.github.youssefrashidy.exceptions.AmbiguousConstructorException;
import io.github.youssefrashidy.exceptions.CircularDependencyException;
import io.github.youssefrashidy.exceptions.UnregisteredDependencyException;
import io.github.youssefrashidy.annotations.Inject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DependencyGraphBuilder {
    private final DependencyResolver dependencyResolver;

    //    private static final Set<Class<?>> UNRESOLVABLE = Set.of(
//            byte.class, short.class, int.class, long.class,
//            float.class, double.class, boolean.class, char.class,
//            Byte.class, Short.class, Integer.class, Long.class,
//            Float.class, Double.class, Boolean.class, Character.class,
//            String.class
//    );
    // removed boxed values now they can be injected
    private static final Set<Class<?>> UNRESOLVABLE = Set.of(
            byte.class, short.class, int.class, long.class,
            float.class, double.class, boolean.class, char.class
    );

    public DependencyGraphBuilder(DependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    public List<BeanDefinition> buildInitializationOrder(ScanMap scanMap, ConfigurationContext configurationContext) {
        System.out.println("[DI] Building initialization order");
        Map<BeanDefinition, Set<BeanDefinition>> classGraph = new HashMap<>();
        Map<BeanDefinition, Integer> indegreeMap = new HashMap<>();
        buildMaps(scanMap, configurationContext, classGraph, indegreeMap);
        System.out.println("[DI] Graph nodes=" + classGraph.size());
        return topologicalSort(classGraph, indegreeMap);
    }

    private void buildMaps(ScanMap scanMap, ConfigurationContext configurationContext, Map<BeanDefinition, Set<BeanDefinition>> classGraph, Map<BeanDefinition, Integer> indegreeMap) {
        System.out.println("[DI] Scanning components=" + scanMap.components().size() + ", configBeans=" + configurationContext.beanDefinitions().size());
        for (var componentDefinition : scanMap.components()) {
            Class<?> cls = componentDefinition.cls();
            System.out.println("[DI] Analyze component=" + cls.getName());
            Constructor<?>[] constructors = cls.getDeclaredConstructors();
            if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
                classGraph.put(componentDefinition, Collections.emptySet());
                indegreeMap.put(componentDefinition, 0);
                continue;
            }

            var annotatedConstructors = Arrays.stream(constructors)
                    .filter(c -> c.isAnnotationPresent(Inject.class))
                    .toArray(Constructor<?>[]::new);

            if (annotatedConstructors.length > 1) {
                throw new AmbiguousConstructorException("Class " + cls.getName() + " has " + annotatedConstructors.length + " constructors - exactly one is required. " +
                        "Annotate the intended constructor with @Inject.");
            }

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

                if (UNRESOLVABLE.contains(type)) {
                    throw new UnregisteredDependencyException(
                            "DI error: cannot inject parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' in bean '" + componentDefinition.identifier() + "' because primitive/value types are not supported. " +
                                    "Use a dedicated configuration bean instead."
                    );
                }

                // add method to check for both components and beans methods
                if (!isResolvable(scanMap, configurationContext, type)) {
                    System.out.println("[DI] Missing dependency type=" + type.getName() + " for bean=" + componentDefinition.identifier());
                    throw new UnregisteredDependencyException(
                            "DI error: missing bean for parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' required by bean '" + componentDefinition.identifier() + "'."
                    );
                }

                var candidateClass = dependencyResolver.resolveParamType(param, scanMap, configurationContext);
                var candidateIdentifier = dependencyResolver.resolveParamIdentifier(param, candidateClass, scanMap, configurationContext);
                BeanDefinition candidateDefinition = configurationContext.beanDefinitions().stream()
                        .filter(definition -> definition.identifier().equals(candidateIdentifier))
                        .map(definition -> (BeanDefinition) definition)
                        .findFirst()
                        .orElseGet(() -> dependencyResolver.resolveDependencyBeanDefinition(candidateClass, scanMap, configurationContext, param));
                System.out.println("[DI] Resolved dependency " + type.getName() + " -> " + candidateClass.getName());
                classGraph.computeIfAbsent(componentDefinition, _ -> new HashSet<>()).add(candidateDefinition);
            }
            indegreeMap.put(componentDefinition, classGraph.get(componentDefinition).size());
        }
        for (var beanDefinition : configurationContext.beanDefinitions()) {
            System.out.println("[DI] Analyze config bean=" + beanDefinition.identifier());
            Parameter[] params = beanDefinition.beanMethod().getParameters();
            if (params.length == 0) {
                classGraph.put(beanDefinition, Collections.emptySet());
                indegreeMap.put(beanDefinition, 0);
                continue;
            }

            for (var param : params) {
                Class<?> type = param.getType();
                if (type == Supplier.class) {
                    Type generic = param.getParameterizedType();
                    if (!(generic instanceof ParameterizedType)) {
                        throw new UnregisteredDependencyException(
                                "DI error: configuration bean method '" + beanDefinition.beanMethod().getDeclaringClass().getName() + "#" +
                                        beanDefinition.beanMethod().getName() + "' has Supplier parameter '" + param.getName() +
                                        "' without a type argument (expected Supplier<Foo>)."
                        );
                    }
                    type = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
                }

                if (UNRESOLVABLE.contains(type)) {
                    throw new UnregisteredDependencyException(
                            "DI error: cannot inject parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' into configuration bean method '" + beanDefinition.beanMethod().getDeclaringClass().getName() + "#" +
                                    beanDefinition.beanMethod().getName() + "' (bean id '" + beanDefinition.identifier() + "') " +
                                    "because primitive/value types are not supported. Use a dedicated configuration bean instead."
                    );
                }

                // add method to check for both components and beans methods
                if (!isResolvable(scanMap, configurationContext, type)) {
                    System.out.println("[DI] Missing dependency type=" + type.getName() + " for config bean=" + beanDefinition.identifier());
                    throw new UnregisteredDependencyException(
                            "DI error: missing bean for parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' required by configuration bean method '" + beanDefinition.beanMethod().getDeclaringClass().getName() + "#" +
                                    beanDefinition.beanMethod().getName() + "' (bean id '" + beanDefinition.identifier() + "')."
                    );
                }

                var candidateClass = dependencyResolver.resolveParamType(param, scanMap, configurationContext);
                var candidateIdentifier = dependencyResolver.resolveParamIdentifier(param, candidateClass, scanMap, configurationContext);
                BeanDefinition candidateDefinition = configurationContext.beanDefinitions().stream()
                        .filter(definition -> definition.identifier().equals(candidateIdentifier))
                        .map(definition -> (BeanDefinition) definition)
                        .findFirst()
                        .orElseGet(() -> dependencyResolver.resolveDependencyBeanDefinition(candidateClass, scanMap, configurationContext, param));
                System.out.println("[DI] Resolved dependency " + type.getName() + " -> " + candidateClass.getName());
                classGraph.computeIfAbsent(beanDefinition, _ -> new HashSet<>()).add(candidateDefinition);
            }
            indegreeMap.put(beanDefinition, classGraph.get(beanDefinition).size());
        }
    }

    private static boolean isResolvable(ScanMap scanMap, ConfigurationContext configurationContext, Class<?> type) {
        boolean isResolvable = false;
        isResolvable = scanMap.components().stream().anyMatch(definition -> type.isAssignableFrom(definition.cls()))
                || scanMap.resolveMap().containsKey(type);
        /*
         * Get the bean definition for the configuration classes
         * Check if the para is either an interface defined as a
         * return type by one of the methods
         * or there is a concrete type match between them
         */

        /*
         * For simplicity here i avoid the case where the injection site is supertype and bean method
         * is subtype handling such case will be very complex
         * so for now 3 out of 4 cases work
         * this also align with recommended practice
         */
        var beanDefinition = configurationContext.beanDefinitions();
        isResolvable |= beanDefinition.stream()
                .map(MethodBeanDefinition::cls)
                .anyMatch(type::isAssignableFrom);

        return isResolvable;
    }

    private List<BeanDefinition> topologicalSort(Map<BeanDefinition, Set<BeanDefinition>> classGraph, Map<BeanDefinition, Integer> indegreeMap) {
        System.out.println("[DI] Topological sort start");
        System.out.println(classGraph);
        Deque<BeanDefinition> zeroDegreeBeans = indegreeMap.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayDeque::new));
        System.out.println(zeroDegreeBeans);

        List<BeanDefinition> initializationOrder = new java.util.ArrayList<>();

        while (!zeroDegreeBeans.isEmpty()) {
            var beanDefinition = zeroDegreeBeans.poll();
            initializationOrder.add(beanDefinition);

            Set<BeanDefinition> dependentBeans = classGraph.entrySet().stream()
                    .filter(entry -> entry.getValue().contains(beanDefinition))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            indegreeMap.entrySet().stream()
                    .filter(entry -> dependentBeans.contains(entry.getKey()))
                    .peek(entry -> entry.setValue(entry.getValue() - 1))
                    .filter(entry -> entry.getValue() == 0)
                    .forEach(entry -> zeroDegreeBeans.push(entry.getKey()));

        }

        if (initializationOrder.size() < classGraph.size()) {
            String unresolved = indegreeMap.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(entry -> entry.getKey().getName())
                    .sorted()
                    .collect(Collectors.joining(", "));
            System.out.println("[DI] Circular dependency detected: " + unresolved);
            throw new CircularDependencyException(
                    "DI error: circular dependency detected among beans: [" + unresolved + "]. " +
                            "Review constructor dependencies to break the cycle."
            );
        }

        System.out.println("[DI] Initialization order size=" + initializationOrder.size());
        return initializationOrder;
    }
}
