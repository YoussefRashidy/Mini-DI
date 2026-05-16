package io.github.youssefrashidy.context;

import io.github.youssefrashidy.annotations.Qualifier;
import io.github.youssefrashidy.annotations.ScopeType;
import io.github.youssefrashidy.exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.exceptions.DuplicateBeanIdentifierException;
import io.github.youssefrashidy.exceptions.UnregisteredDependencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DependencyResolver {
    private static final Logger logger = LoggerFactory.getLogger(DependencyResolver.class);

    public String resolveIdentifier(Class<?> cls) {
        String identifier = cls.isAnnotationPresent(Qualifier.class)
                ? cls.getAnnotation(Qualifier.class).value()
                : cls.getSimpleName();
        logger.debug("Resolved identifier '{}' for type {}", identifier, cls.getName());
        return identifier;
    }

    @Deprecated(forRemoval = true)
    @SuppressWarnings("unused")
    private String resolveIdentifier(Class<?> cls, ConfigurationContext ctx, Parameter param, ScanMap scanMap) {
        logger.trace("Resolving identifier for {} using deprecated path on parameter '{}'", cls.getName(), param.getName());
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
            logger.warn("Ambiguous configuration beans found for {} on parameter '{}': [{}]", cls.getName(), param.getName(), candidates);
            throw new AmbiguousBeanException(
                    "DI error: multiple configuration beans registered for class '" + cls.getName() + "': [" + candidates + "]."
            );
        }

        String identifier = methodCandidates.isEmpty() ? resolveIdentifier(cls) : methodCandidates.getFirst().identifier();
        logger.debug("Deprecated identifier resolution selected '{}' for {}", identifier, cls.getName());

        boolean existsAsComponent = scanMap.components().stream().anyMatch(d -> d.identifier().equals(identifier));
        boolean existsAsMethod    = ctx.beanDefinitions().stream().anyMatch(d -> d.identifier().equals(identifier));

        if (existsAsComponent && existsAsMethod) {
            MethodBeanDefinition conflict = ctx.beanDefinitions().stream()
                    .filter(d -> d.identifier().equals(identifier))
                    .findFirst().orElseThrow();
            logger.warn("Duplicate bean identifier '{}' detected for {} and {}#{}", identifier, cls.getName(),
                    conflict.beanMethod().getDeclaringClass().getName(), conflict.beanMethod().getName());
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
        logger.trace("Resolving parameter type for '{}' declared as {}", param.getName(), type.getName());

        if (type != Supplier.class) {
            logger.debug("Parameter '{}' resolved directly as {}", param.getName(), type.getName());
            return type; // concrete, interface, abstract — all handled uniformly downstream
        }

        // Unwrap Supplier<T>
        Type generic = param.getParameterizedType();
        if (!(generic instanceof ParameterizedType)) {
            logger.warn("Supplier parameter '{}' is missing a type argument", param.getName());
            throw new UnregisteredDependencyException(
                    "DI error: Supplier parameter '" + param.getName() +
                            "' must have a type argument e.g. Supplier<Foo>."
            );
        }

        Class<?> inner = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
        logger.debug("Parameter '{}' is Supplier<{}>", param.getName(), inner.getName());

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
            logger.warn("Supplier<{}> rejected for parameter '{}' because no prototype bean was found", inner.getName(), param.getName());
            throw new UnregisteredDependencyException(
                    "DI error: Supplier<" + inner.getSimpleName() + "> wraps a non-prototype bean. " +
                            "Only prototype beans may be injected as Supplier."
            );
        }

        logger.debug("Supplier parameter '{}' accepted for prototype bean type {}", param.getName(), inner.getName());
        return inner; // caller will wrap the instantiation in a Supplier
    }

    @Deprecated(forRemoval = true)
    @SuppressWarnings("unused")
    private Class<?> resolveConcreteOrInterface(Parameter param, Class<?> type, ScanMap scanMap, ConfigurationContext configurationContext) {
        logger.trace("Resolving concrete/interface type {} for parameter '{}'", type.getName(), param.getName());
        if (!type.isInterface()) {
            logger.debug("Parameter '{}' is concrete type {}", param.getName(), type.getName());
            return type;
        }
        var classes = scanMap.resolveMap().get(type);
        List<ComponentBeanDefinition> componentCandidates = classes == null ? List.of() : classes;
        List<MethodBeanDefinition> methodCandidates = configurationContext.beanDefinitions().stream()
                .filter(definition -> type.isAssignableFrom(definition.cls()))
                .toList();

        logger.debug("Interface {} has {} component candidates and {} method candidates for parameter '{}'",
                type.getName(), componentCandidates.size(), methodCandidates.size(), param.getName());

        if (param.isAnnotationPresent(Qualifier.class)) {
            String val = param.getAnnotation(Qualifier.class).value();
            logger.trace("Applying qualifier '{}' for parameter '{}'", val, param.getName());
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
                logger.warn("Ambiguous qualifier '{}' for interface {} on parameter '{}' -> [{}]", val, type.getName(), param.getName(), joined);
                throw new AmbiguousBeanException(
                        "DI error: multiple beans match qualifier '" + val + "' for interface '" + type.getName() +
                                "' on parameter '" + param.getName() + "': [" + joined + "]."
                );
            }
            if (total == 0) {
                logger.warn("No bean matched qualifier '{}' for interface {} on parameter '{}'", val, type.getName(), param.getName());
                throw new AmbiguousBeanException(
                        "DI error: no bean matches qualifier '" + val + "' for interface '" + type.getName() +
                                "' on parameter '" + param.getName() + "'."
                );
            }
            logger.debug("Qualifier '{}' resolved to {} for parameter '{}'", val,
                    !qualifiedComponents.isEmpty() ? qualifiedComponents.getFirst().cls().getName() : qualifiedMethods.getFirst().cls().getName(),
                    param.getName());
            return !qualifiedComponents.isEmpty() ? qualifiedComponents.getFirst().cls() : qualifiedMethods.getFirst().cls();
        }

        int totalCandidates = componentCandidates.size() + methodCandidates.size();
        if (totalCandidates == 0) {
            logger.warn("No bean implementation registered for interface {} required by parameter '{}'", type.getName(), param.getName());
            throw new UnregisteredDependencyException(
                    "DI error: no bean implementation registered for interface '" + param.getType().getName() +
                            "' required by parameter '" + param.getName() + "'."
            );
        }
        if (totalCandidates > 1) {
            String candidates = componentCandidates.stream().map(definition -> definition.cls().getName()).sorted().collect(Collectors.joining(", "));
            String methodNames = methodCandidates.stream().map(definition -> definition.cls().getName()).sorted().collect(Collectors.joining(", "));
            String joined = String.join(", ", candidates, methodNames).replaceAll("^, |, $", "");
            logger.warn("Ambiguous interface {} for parameter '{}' -> [{}]", type.getName(), param.getName(), joined);
            throw new AmbiguousBeanException(
                    "DI error: multiple beans found for interface '" + type.getName() + "' on parameter '" +
                            param.getName() + "': [" + joined + "]. Add @Qualifier to disambiguate."
            );
        }

        logger.debug("Interface {} resolved uniquely for parameter '{}'", type.getName(), param.getName());
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

        logger.trace("Resolving dependency bean definition for parameter '{}' of type {}", param.getName(), paramType.getName());

        // 1. Collect every bean whose concrete type is assignable to the declared parameter type.
        List<BeanDefinition> candidates = new ArrayList<>();

        scanMap.components().stream()
                .filter(d -> paramType.isAssignableFrom(d.cls()))
                .forEach(candidates::add);
        ctx.beanDefinitions().stream()
                .filter(d -> paramType.isAssignableFrom(d.cls()))
                .forEach(candidates::add);
        logger.debug("Found {} candidate(s) for parameter '{}' of type {}", candidates.size(), param.getName(), paramType.getName());
        // 2. Apply @Qualifier filter if present.
        if (param.isAnnotationPresent(Qualifier.class)) {
            String val = param.getAnnotation(Qualifier.class).value();
            logger.trace("Filtering candidates with qualifier '{}' for parameter '{}'", val, param.getName());
            candidates = candidates.stream()
                    .filter(d -> d.identifier().equals(val))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (candidates.isEmpty()) {
                logger.warn("No bean matches qualifier '{}' for type {} on parameter '{}'", val, paramType.getName(), param.getName());
                throw new AmbiguousBeanException(
                        "DI error: no bean matches qualifier '" + val +
                                "' for type '" + paramType.getName() +
                                "' on parameter '" + param.getName() + "'."
                );
            }
            if (candidates.size() > 1) {
                logger.warn("Multiple beans match qualifier '{}' for type {} on parameter '{}'", val, paramType.getName(), param.getName());
                throw new AmbiguousBeanException(
                        "DI error: multiple beans match qualifier '" + val +
                                "' for type '" + paramType.getName() +
                                "' on parameter '" + param.getName() + "'."
                );
            }
        }

        // 3. No bean matches
        if (candidates.isEmpty()) {
            logger.warn("No bean registered assignable to {} required by parameter '{}'", paramType.getName(), param.getName());
            throw new UnregisteredDependencyException(
                    "DI error: no bean registered assignable to '" + paramType.getName() +
                            "' required by parameter '" + param.getName() + "'."
            );
        }

        // 4. Ambiguity .
        if (candidates.size() > 1) {
            String names = candidates.stream()
                    .map(d -> d.cls().getName())
                    .sorted()
                    .collect(Collectors.joining(", "));
            logger.warn("Multiple beans assignable to {} on parameter '{}' -> [{}]", paramType.getName(), param.getName(), names);
            throw new AmbiguousBeanException(
                    "DI error: multiple beans assignable to '" + paramType.getName() +
                            "' on parameter '" + param.getName() + "': [" + names +
                            "]. Add @Qualifier to disambiguate."
            );
        }

        BeanDefinition resolved = candidates.getFirst();
        logger.debug("Resolved dependency for parameter '{}' to {} ({})", param.getName(), resolved.cls().getName(), resolved.identifier());
        return new DependencyBeanDefinition(resolved.cls(), null, resolved.identifier());
    }
}
