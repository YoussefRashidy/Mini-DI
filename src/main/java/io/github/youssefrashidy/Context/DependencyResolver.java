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

    public String resolveIdentifier(Class<?> cls, ConfigurationContext configurationContext, Parameter parameter) {
        if (configurationContext != null) {
            var methodCandidates = configurationContext.beanDefinitions().stream()
                    .filter(definition -> definition.cls().equals(cls))
                    .filter(definition -> {
                        if (parameter.isAnnotationPresent(Qualifier.class))
                            return parameter.getAnnotation(Qualifier.class).value().equals(definition.identifier());
                        else return true;
                    })
                    .toList();
            if (methodCandidates.size() > 1) {
                String candidates = methodCandidates.stream()
                        .map(MethodBeanDefinition::identifier)
                        .sorted()
                        .collect(Collectors.joining(", "));
                throw new AmbiguousBeanException(
                        "DI error: multiple configuration beans registered for class '" + cls.getName() +
                                "': [" + candidates + "]."
                );
            }
            if (!methodCandidates.isEmpty()) {
                return methodCandidates.getFirst().identifier();
            }
        }
        return resolveIdentifier(cls);
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

    public Class<?> resolveParamType(Parameter param, Map<Class<?>, List<Class<?>>> resolveMap, ConfigurationContext configurationContext) {
        var type = param.getType();

        if (type == Supplier.class) {
            Type generic = param.getParameterizedType();

            if (!(generic instanceof ParameterizedType)) {
                throw new UnregisteredDependencyException(
                        "DI error: Supplier parameter '" + param.getName() + "' must have a type argument e.g. Supplier<Foo>."
                );
            }

            type = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
            type = resolveParamType(param, type, resolveMap, configurationContext);
            if (!type.isAnnotationPresent(Scope.class) || type.getAnnotation(Scope.class).value() != ScopeType.PROTOTYPE) {
                throw new UnregisteredDependencyException(
                        "DI error: Supplier<" + type.getSimpleName() + "> wraps a non-prototype bean. " +
                                "Only prototype beans may be injected as Supplier."
                );
            }
        }
        return resolveParamType(param, type, resolveMap, configurationContext);
    }

    public Class<?> resolveParamType(Parameter param, Class<?> type, Map<Class<?>, List<Class<?>>> resolveMap, ConfigurationContext configurationContext) {
        if (!type.isInterface()) {
            return type;
        }
        var classes = resolveMap.get(type);
        List<Class<?>> componentCandidates = classes == null ? List.of() : classes;
        List<MethodBeanDefinition> methodCandidates = configurationContext == null
                ? List.of()
                : configurationContext.beanDefinitions().stream()
                .filter(definition -> type.isAssignableFrom(definition.cls()))
                .toList();

        if (param.isAnnotationPresent(Qualifier.class)) {
            String val = param.getAnnotation(Qualifier.class).value();
            var qualifiedComponents = componentCandidates.stream()
                    .filter(cls -> cls.isAnnotationPresent(Qualifier.class)
                            && cls.getAnnotation(Qualifier.class).value().equals(val))
                    .toList();
            var qualifiedMethods = methodCandidates.stream()
                    .filter(definition -> definition.identifier().equals(val))
                    .toList();
            int total = qualifiedComponents.size() + qualifiedMethods.size();
            if (total > 1) {
                String candidates = qualifiedComponents.stream().map(Class::getName)
                        .collect(Collectors.joining(", "));
                String methodNames = qualifiedMethods.stream().map(definition -> definition.cls().getName())
                        .collect(Collectors.joining(", "));
                String joined = String.join(", ", candidates, methodNames).replaceAll("^, |, $", "");
                throw new AmbiguousBeanException(
                        "DI error: multiple beans match qualifier '" + val + "' for interface '" + type.getName() +
                                "' on parameter '" + param.getName() + "': [" + joined + "]."
                );
            }
            if (total == 0) {
                throw new AmbiguousBeanException(
                        "DI error: no bean matches qualifier '" + val + "' for interface '" + type.getName() +
                                "' on parameter '" + param.getName() + "'."
                );
            }
            return !qualifiedComponents.isEmpty() ? qualifiedComponents.getFirst() : qualifiedMethods.getFirst().cls();
        }

        int totalCandidates = componentCandidates.size() + methodCandidates.size();
        if (totalCandidates == 0) {
            throw new UnregisteredDependencyException(
                    "DI error: no bean implementation registered for interface '" + param.getType().getName() +
                            "' required by parameter '" + param.getName() + "'."
            );
        }
        if (totalCandidates > 1) {
            String candidates = componentCandidates.stream().map(Class::getName).sorted().collect(Collectors.joining(", "));
            String methodNames = methodCandidates.stream().map(definition -> definition.cls().getName()).sorted().collect(Collectors.joining(", "));
            String joined = String.join(", ", candidates, methodNames).replaceAll("^, |, $", "");
            throw new AmbiguousBeanException(
                    "DI error: multiple beans found for interface '" + type.getName() + "' on parameter '" +
                            param.getName() + "': [" + joined + "]. Add @Qualifier to disambiguate."
            );
        }

        return !componentCandidates.isEmpty() ? componentCandidates.getFirst() : methodCandidates.getFirst().cls();
    }

    public String resolveParamIdentifier(Parameter param, Class<?> type, Map<Class<?>, List<Class<?>>> resolveMap, ConfigurationContext configurationContext) {
        if (!type.isInterface()) {
            return resolveIdentifier(type, configurationContext, param);
        }
        var classes = resolveMap.get(type);
        List<Class<?>> componentCandidates = classes == null ? List.of() : classes;
        List<MethodBeanDefinition> methodCandidates = configurationContext == null
                ? List.of()
                : configurationContext.beanDefinitions().stream()
                .filter(definition -> type.isAssignableFrom(definition.cls()))
                .toList();

        if (param.isAnnotationPresent(Qualifier.class)) {
            String val = param.getAnnotation(Qualifier.class).value();
            var qualifiedComponents = componentCandidates.stream()
                    .filter(cls -> cls.isAnnotationPresent(Qualifier.class)
                            && cls.getAnnotation(Qualifier.class).value().equals(val))
                    .toList();
            var qualifiedMethods = methodCandidates.stream()
                    .filter(definition -> definition.identifier().equals(val))
                    .toList();
            int total = qualifiedComponents.size() + qualifiedMethods.size();
            if (total > 1) {
                String candidates = qualifiedComponents.stream().map(Class::getName)
                        .collect(Collectors.joining(", "));
                String methodNames = qualifiedMethods.stream().map(definition -> definition.cls().getName())
                        .collect(Collectors.joining(", "));
                String joined = String.join(", ", candidates, methodNames).replaceAll("^, |, $", "");
                throw new AmbiguousBeanException(
                        "DI error: multiple beans match qualifier '" + val + "' for interface '" + type.getName() +
                                "' on parameter '" + param.getName() + "': [" + joined + "]."
                );
            }
            if (total == 0) {
                throw new AmbiguousBeanException(
                        "DI error: no bean matches qualifier '" + val + "' for interface '" + type.getName() +
                                "' on parameter '" + param.getName() + "'."
                );
            }
            return !qualifiedComponents.isEmpty() ? val : qualifiedMethods.getFirst().identifier();
        }

        int totalCandidates = componentCandidates.size() + methodCandidates.size();
        if (totalCandidates == 0) {
            throw new UnregisteredDependencyException(
                    "DI error: no bean implementation registered for interface '" + param.getType().getName() +
                            "' required by parameter '" + param.getName() + "'."
            );
        }
        if (totalCandidates > 1) {
            String candidates = componentCandidates.stream().map(Class::getName).sorted().collect(Collectors.joining(", "));
            String methodNames = methodCandidates.stream().map(definition -> definition.cls().getName()).sorted().collect(Collectors.joining(", "));
            String joined = String.join(", ", candidates, methodNames).replaceAll("^, |, $", "");
            throw new AmbiguousBeanException(
                    "DI error: multiple beans found for interface '" + type.getName() + "' on parameter '" +
                            param.getName() + "': [" + joined + "]. Add @Qualifier to disambiguate."
            );
        }

        return !componentCandidates.isEmpty()
                ? resolveIdentifier(componentCandidates.getFirst(), configurationContext, param)
                : methodCandidates.getFirst().identifier();
    }

    public AnnotationBeanDefinition resolveAnnotationBeanDefinition(Class<?> cls) {
        return new AnnotationBeanDefinition(cls, resolveIdentifier(cls));
    }

    public AnnotationBeanDefinition resolveAnnotationBeanDefinition(Class<?> cls, ConfigurationContext configurationContext, Parameter parameter) {
        return new AnnotationBeanDefinition(cls, resolveIdentifier(cls, configurationContext, parameter));
    }

    public AnnotationBeanDefinition resolveAnnotationBeanDefinition(Class<?> cls, ConfigurationContext configurationContext) {
        return new AnnotationBeanDefinition(cls, resolveIdentifier(cls));
    }
}
