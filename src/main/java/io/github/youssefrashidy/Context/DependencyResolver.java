package io.github.youssefrashidy.Context;

import io.github.youssefrashidy.Exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.Exceptions.DuplicateBeanIdentifierException;
import io.github.youssefrashidy.Exceptions.UnregisteredDependencyException;
import io.github.youssefrashidy.annotations.Qualifier;
import io.github.youssefrashidy.annotations.ScopeType;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DependencyResolver {
    public String resolveIdentifier(Class<?> cls) {
        return cls.isAnnotationPresent(Qualifier.class)
                ? cls.getAnnotation(Qualifier.class).value()
                : cls.getSimpleName();
    }

    public String resolveIdentifier(Class<?> cls, ConfigurationContext configurationContext, Parameter parameter) {
        return resolveIdentifier(cls, configurationContext, parameter, null);
    }

    private String resolveIdentifier(Class<?> cls, ConfigurationContext ctx, Parameter param, ScanMap scanMap) {
        var methodCandidates = ctx.beanDefinitions().stream()
                .filter(def -> def.cls().equals(cls))
                .filter(def -> !param.isAnnotationPresent(Qualifier.class)
                        || param.getAnnotation(Qualifier.class).value().equals(def.identifier()))
                .toList();

        if (methodCandidates.size() > 1) {
            String candidates = methodCandidates.stream()
                    .map(MethodBeanDefinition::identifier)
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new AmbiguousBeanException(
                    "DI error: multiple configuration beans registered for class '" + cls.getName() + "': [" + candidates + "]."
            );
        }

        String identifier = methodCandidates.isEmpty() ? resolveIdentifier(cls) : methodCandidates.getFirst().identifier();

        boolean existsAsComponent = scanMap.components().stream().anyMatch(d -> d.identifier().equals(identifier));
        boolean existsAsMethod    = ctx.beanDefinitions().stream().anyMatch(d -> d.identifier().equals(identifier));

        if (existsAsComponent && existsAsMethod) {
            MethodBeanDefinition conflict = ctx.beanDefinitions().stream()
                    .filter(d -> d.identifier().equals(identifier))
                    .findFirst().orElseThrow();
            throw new DuplicateBeanIdentifierException(
                    "DI error: duplicate bean identifier '" + identifier + "' for component '" + cls.getName() +
                            "' and configuration bean '" + conflict.beanMethod().getDeclaringClass().getName() +
                            "#" + conflict.beanMethod().getName() + "'."
            );
        }

        return identifier;
    }

    public Class<?> resolveParamType(Parameter param, ScanMap scanMap, ConfigurationContext configurationContext) {
        var type = param.getType();

        if (type == Supplier.class) {
            Type generic = param.getParameterizedType();

            if (!(generic instanceof ParameterizedType)) {
                throw new UnregisteredDependencyException(
                        "DI error: Supplier parameter '" + param.getName() + "' must have a type argument e.g. Supplier<Foo>."
                );
            }

            type = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
            type = resolveConcreteOrInterface(param, type, scanMap, configurationContext);
            Class<?> resolvedType = type;
            ComponentBeanDefinition definition = scanMap.components().stream()
                    .filter(component -> component.cls().equals(resolvedType))
                    .findFirst()
                    .orElse(null);
            if (definition == null || definition.scope() != ScopeType.PROTOTYPE) {
                throw new UnregisteredDependencyException(
                        "DI error: Supplier<" + type.getSimpleName() + "> wraps a non-prototype bean. " +
                                "Only prototype beans may be injected as Supplier."
                );
            }
        }
        return resolveConcreteOrInterface(param, type, scanMap, configurationContext);
    }

    private Class<?> resolveConcreteOrInterface(Parameter param, Class<?> type, ScanMap scanMap, ConfigurationContext configurationContext) {
        if (!type.isInterface()) {
            return type;
        }
        var classes = scanMap.resolveMap().get(type);
        List<ComponentBeanDefinition> componentCandidates = classes == null ? List.of() : classes;
        List<MethodBeanDefinition> methodCandidates = configurationContext.beanDefinitions().stream()
                .filter(definition -> type.isAssignableFrom(definition.cls()))
                .toList();

        if (param.isAnnotationPresent(Qualifier.class)) {
            String val = param.getAnnotation(Qualifier.class).value();
            var qualifiedComponents = componentCandidates.stream()
                    .filter(definition -> definition.identifier().equals(val))
                    .toList();
            var qualifiedMethods = methodCandidates.stream()
                    .filter(definition -> definition.identifier().equals(val))
                    .toList();
            int total = qualifiedComponents.size() + qualifiedMethods.size();
            if (total > 1) {
                String candidates = qualifiedComponents.stream().map(definition -> definition.cls().getName())
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
            return !qualifiedComponents.isEmpty() ? qualifiedComponents.getFirst().cls() : qualifiedMethods.getFirst().cls();
        }

        int totalCandidates = componentCandidates.size() + methodCandidates.size();
        if (totalCandidates == 0) {
            throw new UnregisteredDependencyException(
                    "DI error: no bean implementation registered for interface '" + param.getType().getName() +
                            "' required by parameter '" + param.getName() + "'."
            );
        }
        if (totalCandidates > 1) {
            String candidates = componentCandidates.stream().map(definition -> definition.cls().getName()).sorted().collect(Collectors.joining(", "));
            String methodNames = methodCandidates.stream().map(definition -> definition.cls().getName()).sorted().collect(Collectors.joining(", "));
            String joined = String.join(", ", candidates, methodNames).replaceAll("^, |, $", "");
            throw new AmbiguousBeanException(
                    "DI error: multiple beans found for interface '" + type.getName() + "' on parameter '" +
                            param.getName() + "': [" + joined + "]. Add @Qualifier to disambiguate."
            );
        }

        return !componentCandidates.isEmpty() ? componentCandidates.getFirst().cls() : methodCandidates.getFirst().cls();
    }

    public String resolveParamIdentifier(Parameter param, Class<?> type, ScanMap scanMap, ConfigurationContext configurationContext) {
        if (!type.isInterface()) {
            return resolveIdentifier(type, configurationContext, param, scanMap);
        }
        var classes = scanMap.resolveMap().get(type);
        List<ComponentBeanDefinition> componentCandidates = classes == null ? List.of() : classes;
        List<MethodBeanDefinition> methodCandidates = configurationContext.beanDefinitions().stream()
                .filter(definition -> type.isAssignableFrom(definition.cls()))
                .toList();

        if (param.isAnnotationPresent(Qualifier.class)) {
            String val = param.getAnnotation(Qualifier.class).value();
            var qualifiedComponents = componentCandidates.stream()
                    .filter(definition -> definition.identifier().equals(val))
                    .toList();
            var qualifiedMethods = methodCandidates.stream()
                    .filter(definition -> definition.identifier().equals(val))
                    .toList();
            int total = qualifiedComponents.size() + qualifiedMethods.size();
            if (total > 1) {
                String candidates = qualifiedComponents.stream().map(definition -> definition.cls().getName())
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
            String candidates = componentCandidates.stream().map(definition -> definition.cls().getName()).sorted().collect(Collectors.joining(", "));
            String methodNames = methodCandidates.stream().map(definition -> definition.cls().getName()).sorted().collect(Collectors.joining(", "));
            String joined = String.join(", ", candidates, methodNames).replaceAll("^, |, $", "");
            throw new AmbiguousBeanException(
                    "DI error: multiple beans found for interface '" + type.getName() + "' on parameter '" +
                            param.getName() + "': [" + joined + "]. Add @Qualifier to disambiguate."
            );
        }

        return !componentCandidates.isEmpty()
                ? resolveIdentifier(componentCandidates.getFirst().cls(), configurationContext, param, scanMap)
                : methodCandidates.getFirst().identifier();
    }

    public DependencyBeanDefinition resolveDependencyBeanDefinition(Class<?> cls, ScanMap scanMap,
                                                                    ConfigurationContext ctx, Parameter param) {
        List<BeanDefinition> candidates = new ArrayList<>();

        scanMap.components().stream()
                .filter(d -> d.cls().equals(cls))
                .forEach(candidates::add);

        ctx.beanDefinitions().stream()
                .filter(d -> d.cls().equals(cls))
                .forEach(candidates::add);

        if (candidates.isEmpty())
            throw new UnregisteredDependencyException(
                    "DI error: no bean registered for class '" + cls.getName() +
                            "' required by parameter '" + param.getName() + "'."
            );

        if (candidates.size() > 1) {
            if (param.isAnnotationPresent(Qualifier.class)) {
                String val = param.getAnnotation(Qualifier.class).value();
                candidates = candidates.stream()
                        .filter(d -> d.identifier().equals(val))
                        .toList();
                if (candidates.isEmpty())
                    throw new AmbiguousBeanException(
                            "DI error: no bean matches qualifier '" + val +
                                    "' for class '" + cls.getName() + "' on parameter '" + param.getName() + "'."
                    );
                if (candidates.size() > 1)
                    throw new AmbiguousBeanException(
                            "DI error: multiple beans match qualifier '" + val +
                                    "' for class '" + cls.getName() + "' on parameter '" + param.getName() + "'."
                    );
            } else {
                throw new AmbiguousBeanException(
                        "DI error: multiple beans registered for class '" + cls.getName() +
                                "' on parameter '" + param.getName() + "'. Add @Qualifier to disambiguate."
                );
            }
        }

        // recheck this method

        BeanDefinition resolved = candidates.getFirst();
        resolveIdentifier(resolved.cls(), ctx, param, scanMap); // global name conflict check
        return new DependencyBeanDefinition(resolved.cls(), null,resolved.identifier());
    }
}
