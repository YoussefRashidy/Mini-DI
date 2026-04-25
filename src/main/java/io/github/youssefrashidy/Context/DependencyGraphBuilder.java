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

	public List<Class<?>> buildInitializationOrder(ScanMap scanMap, ConfigurationContext configurationContext) {
		Map<Class<?>, Set<Class<?>>> classGraph = new HashMap<>();
		Map<Class<?>, Integer> indegreeMap = new HashMap<>();
		buildMaps(scanMap,configurationContext ,classGraph, indegreeMap);
		return topologicalSort(classGraph, indegreeMap);
	}

	private void buildMaps(ScanMap scanMap, ConfigurationContext configurationContext, Map<Class<?>, Set<Class<?>>> classGraph, Map<Class<?>, Integer> indegreeMap) {
		for (var cls : scanMap.componentList()) {
			Constructor<?>[] constructors = cls.getDeclaredConstructors();
			if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
				classGraph.put(cls, Collections.emptySet());
				indegreeMap.put(cls, 0);
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
									"' in bean '" + cls.getName() + "' because primitive/value types are not supported. " +
									"Use a dedicated configuration bean instead."
					);
				}

				// add method to check for both components and beans methods
				if (isResolvable(scanMap, configurationContext,type)) {
					throw new UnregisteredDependencyException(
							"DI error: missing bean for parameter '" + param.getName() + "' of type '" + type.getName() +
									"' required by bean '" + cls.getName() + "'."
					);
				}

				var candidateClass = dependencyResolver.resolveParamType(param, scanMap.resolveMap());
				classGraph.computeIfAbsent(cls, _ -> new HashSet<>()).add(candidateClass);
			}
			indegreeMap.put(cls, classGraph.get(cls).size());
		}
	}

	private static boolean isResolvable(ScanMap scanMap,ConfigurationContext configurationContext ,Class<?> type) {
		return !type.isAnnotationPresent(Component.class) && !scanMap.resolveMap().containsKey(type);
	}

	private List<Class<?>> topologicalSort(Map<Class<?>, Set<Class<?>>> classGraph, Map<Class<?>, Integer> indegreeMap) {
		Deque<Class<?>> zeroDegreeClasses = indegreeMap.entrySet().stream()
				.filter(entry -> entry.getValue() == 0)
				.map(Map.Entry::getKey)
				.collect(Collectors.toCollection(ArrayDeque::new));

		List<Class<?>> initializationOrder = new java.util.ArrayList<>();

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
}
