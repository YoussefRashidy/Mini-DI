package io.github.youssefrashidy.Context;

import io.github.youssefrashidy.Exceptions.AmbiguousConstructorException;
import io.github.youssefrashidy.Exceptions.CircularDependencyException;
import io.github.youssefrashidy.Exceptions.UnregisteredDependencyException;
import io.github.youssefrashidy.annotations.Component;
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

    private static final Set<Class<?>> UNRESOLVABLE = Set.of(
            byte.class, short.class, int.class, long.class,
            float.class, double.class, boolean.class, char.class,
            Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, Boolean.class, Character.class,
            String.class
    );

    public DependencyGraphBuilder(DependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    public List<BeanDefinition> buildInitializationOrder(ScanMap scanMap, ConfigurationContext configurationContext) {
        Map<BeanDefinition, Set<BeanDefinition>> classGraph = new HashMap<>();
        Map<BeanDefinition, Integer> indegreeMap = new HashMap<>();
        buildMaps(scanMap, configurationContext, classGraph, indegreeMap);
        return topologicalSort(classGraph, indegreeMap);
    }

    private void buildMaps(ScanMap scanMap, ConfigurationContext configurationContext, Map<BeanDefinition, Set<BeanDefinition>> classGraph, Map<BeanDefinition, Integer> indegreeMap) {
        for (var cls : scanMap.componentList()) {
            AnnotationBeanDefinition annotationDefinition = dependencyResolver.resolveAnnotationBeanDefinition(cls);
            Constructor<?>[] constructors = cls.getDeclaredConstructors();
            if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
                classGraph.put(dependencyResolver.resolveAnnotationBeanDefinition(cls), Collections.emptySet());
                indegreeMap.put(dependencyResolver.resolveAnnotationBeanDefinition(cls), 0);
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
                                    "' in bean '" + annotationDefinition.identifier() + "' because primitive/value types are not supported. " +
                                    "Use a dedicated configuration bean instead."
                    );
                }

                // add method to check for both components and beans methods
                if (!isResolvable(scanMap, configurationContext, type)) {
                    throw new UnregisteredDependencyException(
                            "DI error: missing bean for parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' required by bean '" + annotationDefinition.identifier() + "'."
                    );
                }

                var candidateClass = dependencyResolver.resolveParamType(param, scanMap.resolveMap());
                classGraph.computeIfAbsent(dependencyResolver.resolveAnnotationBeanDefinition(cls), _ -> new HashSet<>()).add(dependencyResolver.resolveAnnotationBeanDefinition(candidateClass));
            }
            indegreeMap.put(dependencyResolver.resolveAnnotationBeanDefinition(cls), classGraph.get(dependencyResolver.resolveAnnotationBeanDefinition(cls)).size());
        }
        for (var beanDefinition : configurationContext.beanDefinitions()) {
            /*
             * Two cases
             * 1. bean has no parameter then fine added it
             * (note it may call other bean methods that do have parameters so the check recursive)
             * 2. bean has explicitly parameters only
             * (the same idea it might depend on beans that depend on other bean)
             * Actually this is a hallucination concern it doesn't really matter
             * it will be handled automatically with topo sort
             */
            Parameter[] params = beanDefinition.beanMethod().getParameters();

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
                    throw new UnregisteredDependencyException(
                            "DI error: missing bean for parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' required by configuration bean method '" + beanDefinition.beanMethod().getDeclaringClass().getName() + "#" +
                                    beanDefinition.beanMethod().getName() + "' (bean id '" + beanDefinition.identifier() + "')."
                    );
                }

                var candidateClass = dependencyResolver.resolveParamType(param, scanMap.resolveMap());
                classGraph.computeIfAbsent(beanDefinition, _ -> new HashSet<>()).add(dependencyResolver.resolveAnnotationBeanDefinition(candidateClass));
            }
            indegreeMap.put(beanDefinition, classGraph.get(beanDefinition).size());
        }
    }

    private static boolean isResolvable(ScanMap scanMap, ConfigurationContext configurationContext, Class<?> type) {
        boolean isResolvable = false;
        isResolvable = type.isAnnotationPresent(Component.class) && !scanMap.resolveMap().containsKey(type);
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
        Deque<BeanDefinition> zeroDegreeBeans = indegreeMap.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayDeque::new));

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
            throw new CircularDependencyException(
                    "DI error: circular dependency detected among beans: [" + unresolved + "]. " +
                            "Review constructor dependencies to break the cycle."
            );
        }

        return initializationOrder;
    }
}
