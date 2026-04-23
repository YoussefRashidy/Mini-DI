package io.github.youssefrashidy.Context;

import io.github.youssefrashidy.Exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.Exceptions.UnregisteredDependencyException;
import io.github.youssefrashidy.annotations.Qualifier;
import io.github.youssefrashidy.annotations.Scope;
import io.github.youssefrashidy.annotations.ScopeType;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DependencyResolver {
	public String resolveIdentifier(Class<?> cls) {
		return cls.isAnnotationPresent(Qualifier.class)
				? cls.getAnnotation(Qualifier.class).value()
				: cls.getSimpleName();
	}

	public Class<?> resolveParamType(Parameter param, Map<Class<?>, List<Class<?>>> resolveMap) {
		var type = param.getType();

		if (type == Supplier.class) {
			Type generic = param.getParameterizedType();

			if (!(generic instanceof ParameterizedType)) {
				throw new UnregisteredDependencyException(
						"DI error: Supplier parameter '" + param.getName() + "' must have a type argument e.g. Supplier<Foo>."
				);
			}

			type = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
			type = resolveParamType(param, type, resolveMap);
			if (!type.isAnnotationPresent(Scope.class) || type.getAnnotation(Scope.class).value() != ScopeType.PROTOTYPE) {
				throw new UnregisteredDependencyException(
						"DI error: Supplier<" + type.getSimpleName() + "> wraps a non-prototype bean. " +
								"Only prototype beans may be injected as Supplier."
				);
			}
		}
		return resolveParamType(param, type, resolveMap);
	}

	public Class<?> resolveParamType(Parameter param, Class<?> type, Map<Class<?>, List<Class<?>>> resolveMap) {
		if (!type.isInterface()) {
			return type;
		}
		var classes = resolveMap.get(type);

		if (classes == null || classes.isEmpty()) {
			throw new UnregisteredDependencyException(
					"DI error: no bean implementation registered for interface '" + param.getType().getName() +
							"' required by parameter '" + param.getName() + "'."
			);
		}

		if (classes.size() > 1) {
			if (param.isAnnotationPresent(Qualifier.class)) {
				String val = param.getAnnotation(Qualifier.class).value();
				var candidates = classes.stream()
						.filter(cls -> cls.isAnnotationPresent(Qualifier.class)
								&& cls.getAnnotation(Qualifier.class).value().equals(val))
						.toList();
				if (candidates.size() > 1) {
					throw new AmbiguousBeanException(
							"DI error: multiple beans match qualifier '" + val + "' for interface '" + type.getName() +
									"' on parameter '" + param.getName() + "': [" +
									candidates.stream().map(Class::getName).sorted().collect(Collectors.joining(", ")) + "]."
					);
				}
				if (candidates.isEmpty()) {
					throw new AmbiguousBeanException(
							"DI error: no bean matches qualifier '" + val + "' for interface '" + type.getName() +
									"' on parameter '" + param.getName() + "'."
					);
				}

				return candidates.getFirst();
			}

			String candidates = classes.stream().map(Class::getName).sorted().collect(Collectors.joining(", "));
			throw new AmbiguousBeanException(
					"DI error: multiple beans found for interface '" + type.getName() + "' on parameter '" +
							param.getName() + "': [" + candidates + "]. Add @Qualifier to disambiguate."
			);
		}

		return classes.getFirst();
	}

}
