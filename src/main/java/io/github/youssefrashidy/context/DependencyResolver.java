package io.github.youssefrashidy.context;

import io.github.youssefrashidy.exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.exceptions.DuplicateBeanIdentifierException;
import io.github.youssefrashidy.exceptions.UnregisteredDependencyException;
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

    @Deprecated
    private String resolveIdentifier(Class<?> cls, ConfigurationContext ctx, Parameter param, ScanMap scanMap) {
        var methodCandidates = ctx.beanDefinitions().stream()
                .filter(def -> cls.isAssignableFrom(def.cls()))
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

        if (type != Supplier.class) {
            return type; // concrete, interface, abstract — all handled uniformly downstream
        }

        // Unwrap Supplier<T>
        Type generic = param.getParameterizedType();
        if (!(generic instanceof ParameterizedType)) {
            throw new UnregisteredDependencyException(
                    "DI error: Supplier parameter '" + param.getName() +
                            "' must have a type argument e.g. Supplier<Foo>."
            );
        }

        Class<?> inner = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];

        // Validate that the inner type resolves at least one prototype
        List<BeanDefinition> candidates = new ArrayList<>();

        scanMap.components().stream()
                .filter(d -> inner.isAssignableFrom(d.cls()))
                .forEach(candidates::add);

        configurationContext.beanDefinitions().stream()
                .filter(d -> inner.isAssignableFrom(d.cls()))
                .forEach(candidates::add);

        boolean hasPrototype = candidates.stream().anyMatch(d -> d.scope() == ScopeType.PROTOTYPE);

        if (candidates.isEmpty() || !hasPrototype) {
            throw new UnregisteredDependencyException(
                    "DI error: Supplier<" + inner.getSimpleName() + "> wraps a non-prototype bean. " +
                            "Only prototype beans may be injected as Supplier."
            );
        }

        return inner; // caller will wrap the instantiation in a Supplier
    }

    @Deprecated
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

    public String resolveParamIdentifier(Parameter param, Class<?> paramType,
                                         ScanMap scanMap, ConfigurationContext ctx) {
        // resolveDependencyBeanDefinition already does the full isAssignableFrom search
        // with qualifier filtering and ambiguity checks — just reuse it.
        return resolveDependencyBeanDefinition(paramType, scanMap, ctx, param).identifier();
    }

    public DependencyBeanDefinition resolveDependencyBeanDefinition(
            Class<?> paramType,        // raw declared type (Vehicle, Greeter, DataSource, ...)
            ScanMap scanMap,
            ConfigurationContext ctx,
            Parameter param) {

        // 1. Collect every bean whose concrete type is assignable to the declared parameter type.
        List<BeanDefinition> candidates = new ArrayList<>();

        scanMap.components().stream()
                .filter(d -> paramType.isAssignableFrom(d.cls()))
                .forEach(candidates::add);
        ctx.beanDefinitions().stream()
                .filter(d -> paramType.isAssignableFrom(d.cls()))
                .forEach(candidates::add);
        // 2. Apply @Qualifier filter if present.
        if (param.isAnnotationPresent(Qualifier.class)) {
            String val = param.getAnnotation(Qualifier.class).value();
            candidates = candidates.stream()
                    .filter(d -> d.identifier().equals(val))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (candidates.isEmpty())
                throw new AmbiguousBeanException(
                        "DI error: no bean matches qualifier '" + val +
                                "' for type '" + paramType.getName() +
                                "' on parameter '" + param.getName() + "'."
                );
            if (candidates.size() > 1)
                throw new AmbiguousBeanException(
                        "DI error: multiple beans match qualifier '" + val +
                                "' for type '" + paramType.getName() +
                                "' on parameter '" + param.getName() + "'."
                );
        }

        // 3. No bean matches
        if (candidates.isEmpty())
            throw new UnregisteredDependencyException(
                    "DI error: no bean registered assignable to '" + paramType.getName() +
                            "' required by parameter '" + param.getName() + "'."
            );

        // 4. Ambiguity .
        if (candidates.size() > 1) {
            String names = candidates.stream()
                    .map(d -> d.cls().getName())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new AmbiguousBeanException(
                    "DI error: multiple beans assignable to '" + paramType.getName() +
                            "' on parameter '" + param.getName() + "': [" + names +
                            "]. Add @Qualifier to disambiguate."
            );
        }

        BeanDefinition resolved = candidates.getFirst();
        return new DependencyBeanDefinition(resolved.cls(), null, resolved.identifier());
    }
}
