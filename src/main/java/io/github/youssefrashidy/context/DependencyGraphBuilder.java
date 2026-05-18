package io.github.youssefrashidy.context;

import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.exceptions.AmbiguousConstructorException;
import io.github.youssefrashidy.exceptions.CircularDependencyException;
import io.github.youssefrashidy.exceptions.DuplicateBeanIdentifierException;
import io.github.youssefrashidy.exceptions.UnregisteredDependencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilder.class);
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
        logger.info("Building initialization order for {} component(s) and {} configuration bean(s)",
                scanMap.components().size(), configurationContext.beanDefinitions().size());
        Map<BeanDefinition, Set<BeanDefinition>> classGraph = new HashMap<>();
        Map<BeanDefinition, Integer> indegreeMap = new HashMap<>();
        buildMaps(scanMap, configurationContext, classGraph, indegreeMap);
        logger.debug("Dependency graph contains {} node(s)", classGraph.size());
        return topologicalSort(classGraph, indegreeMap);
    }

    private void buildMaps(ScanMap scanMap, ConfigurationContext configurationContext, Map<BeanDefinition, Set<BeanDefinition>> classGraph, Map<BeanDefinition, Integer> indegreeMap) {
        logger.debug("Scanning {} component(s) and {} configuration bean(s)",
                scanMap.components().size(), configurationContext.beanDefinitions().size());
        for (var componentDefinition : scanMap.components()) {
            Class<?> cls = componentDefinition.cls();
            logger.trace("Analyzing component bean {} ({})", componentDefinition.identifier(), cls.getName());
            Constructor<?>[] constructors = cls.getDeclaredConstructors();
            if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
                logger.debug("Component {} uses implicit no-arg constructor", cls.getName());
                classGraph.put(componentDefinition, Collections.emptySet());
                indegreeMap.put(componentDefinition, 0);
                continue;
            }

            var annotatedConstructors = Arrays.stream(constructors)
                    .filter(c -> c.isAnnotationPresent(Inject.class))
                    .toArray(Constructor<?>[]::new);

            if (annotatedConstructors.length > 1) {
                logger.warn("Multiple @Inject constructors found on {}", cls.getName());
                throw new AmbiguousConstructorException("Class " + cls.getName() + " has " + annotatedConstructors.length + " constructors - exactly one is required. " +
                        "Annotate the intended constructor with @Inject.");
            }

            var constructor = annotatedConstructors[0];
            logger.debug("Using constructor {} for component {}", constructor, cls.getName());
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
                    logger.warn("Unresolvable primitive/value parameter '{}' of type {} on component {}", param.getName(), type.getName(), componentDefinition.identifier());
                    throw new UnregisteredDependencyException(
                            "DI error: cannot inject parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' in bean '" + componentDefinition.identifier() + "' because primitive/value types are not supported. " +
                                    "Use a dedicated configuration bean instead."
                    );
                }

                // add method to check for both components and beans methods
                if (isUnresolvable(scanMap, configurationContext, type)) {
                    logger.warn("Missing dependency type {} for bean {}", type.getName(), componentDefinition.identifier());
                    throw new UnregisteredDependencyException(
                            "DI error: missing bean for parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' required by bean '" + componentDefinition.identifier() + "'."
                    );
                }

                var candidateClass = dependencyResolver.resolveParamType(param, scanMap, configurationContext);
                BeanDefinition candidateDefinition = dependencyResolver.resolveDependencyBeanDefinition(candidateClass, scanMap, configurationContext, param);
                logger.debug("Resolved dependency {} -> {} for bean {}", type.getName(), candidateClass.getName(), componentDefinition.identifier());
                classGraph.computeIfAbsent(componentDefinition, _ -> new HashSet<>()).add(candidateDefinition);
            }
            indegreeMap.put(componentDefinition, classGraph.get(componentDefinition).size());
        }
        for (var beanDefinition : configurationContext.beanDefinitions()) {
            logger.trace("Analyzing config bean {} ({})", beanDefinition.identifier(), beanDefinition.beanMethod().getDeclaringClass().getName() + "#" + beanDefinition.beanMethod().getName());
            Parameter[] params = beanDefinition.beanMethod().getParameters();
            if (params.length == 0) {
                logger.debug("Config bean {} has no dependencies", beanDefinition.identifier());
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
                    logger.warn("Unresolvable primitive/value parameter '{}' of type {} on config bean {}", param.getName(), type.getName(), beanDefinition.identifier());
                    throw new UnregisteredDependencyException(
                            "DI error: cannot inject parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' into configuration bean method '" + beanDefinition.beanMethod().getDeclaringClass().getName() + "#" +
                                    beanDefinition.beanMethod().getName() + "' (bean id '" + beanDefinition.identifier() + "') " +
                                    "because primitive/value types are not supported. Use a dedicated configuration bean instead."
                    );
                }

                // add method to check for both components and beans methods
                if (isUnresolvable(scanMap, configurationContext, type)) {
                    logger.warn("Missing dependency type {} for config bean {}", type.getName(), beanDefinition.identifier());
                    throw new UnregisteredDependencyException(
                            "DI error: missing bean for parameter '" + param.getName() + "' of type '" + type.getName() +
                                    "' required by configuration bean method '" + beanDefinition.beanMethod().getDeclaringClass().getName() + "#" +
                                    beanDefinition.beanMethod().getName() + "' (bean id '" + beanDefinition.identifier() + "')."
                    );
                }

                var candidateClass = dependencyResolver.resolveParamType(param, scanMap, configurationContext);
                BeanDefinition candidateDefinition = dependencyResolver.resolveDependencyBeanDefinition(candidateClass, scanMap, configurationContext, param);
                logger.debug("Resolved dependency {} -> {} for config bean {}", type.getName(), candidateClass.getName(), beanDefinition.identifier());
                classGraph.computeIfAbsent(beanDefinition, _ -> new HashSet<>()).add(candidateDefinition);
            }
            indegreeMap.put(beanDefinition, classGraph.get(beanDefinition).size());
        }
    }

    private static boolean isUnresolvable(ScanMap scanMap, ConfigurationContext configurationContext, Class<?> type) {
        boolean isResolvable = scanMap.components().stream().anyMatch(definition -> type.isAssignableFrom(definition.cls()))
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

        return !isResolvable;
    }

    private List<BeanDefinition> topologicalSort(Map<BeanDefinition, Set<BeanDefinition>> classGraph, Map<BeanDefinition, Integer> indegreeMap) {
        logger.info("Starting topological sort");
        logger.trace("Dependency graph: {}", classGraph);
        Deque<BeanDefinition> zeroDegreeBeans = indegreeMap.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayDeque::new));
        logger.debug("Initial zero-indegree beans: {}", zeroDegreeBeans);

        List<BeanDefinition> initializationOrder = new java.util.ArrayList<>();

        while (!zeroDegreeBeans.isEmpty()) {
            var beanDefinition = zeroDegreeBeans.poll();
            initializationOrder.add(beanDefinition);
            logger.trace("Selected bean {} for initialization order", beanDefinition.identifier());

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
            logger.error("Circular dependency detected among beans: {}", unresolved);
            throw new CircularDependencyException(
                    "DI error: circular dependency detected among beans: [" + unresolved + "]. " +
                            "Review constructor dependencies to break the cycle."
            );
        }

        logger.info("Initialization order computed successfully with {} bean(s)", initializationOrder.size());
        return initializationOrder;
    }

    private void validateUniqueIdentifiers(List<BeanDefinition> definitions) {
        Set<String> identifiers = new HashSet<>();
        for (BeanDefinition definition : definitions) {
            String identifier = definition.identifier();
            if (!identifiers.add(identifier)) {
                throw new DuplicateBeanIdentifierException(
                        "DI error: duplicate bean identifier '" + identifier + "' in configuration classes. " +
                                "Identifiers must be unique."
                );
            }
        }
    }
}
