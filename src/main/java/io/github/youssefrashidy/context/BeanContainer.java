package io.github.youssefrashidy.context;

import io.github.youssefrashidy.annotations.Scope;
import io.github.youssefrashidy.annotations.ScopeType;
import io.github.youssefrashidy.exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.exceptions.DuplicateBeanIdentifierException;
import io.github.youssefrashidy.exceptions.UnregisteredDependencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BeanContainer {
    private static final Logger logger = LoggerFactory.getLogger(BeanContainer.class);
    private final Map<String, Supplier<?>> beanRegistry = new HashMap<>();
    private final Map<String, BeanDefinition> definitions = new HashMap<>();
    private final Map<Class<?>, List<String>> typeIndex = new HashMap<>();

    @Deprecated(forRemoval= true)
    public void registerBean(Class<?> cls, String identifier, Supplier<?> supplier) {
        logger.warn("registerBean(Class, String, Supplier) is deprecated; registering {} as '{}'", cls.getName(), identifier);
        registerBean(new ComponentBeanDefinition(cls, resolveScope(cls), identifier), supplier);
    }

    public void registerBean(BeanDefinition definition, Supplier<?> supplier) {
        String identifier = definition.identifier();
        logger.debug("Registering bean '{}' for type {} with scope {}", identifier, definition.cls().getName(), definition.scope());
        BeanDefinition existing = definitions.get(identifier);
        if (existing != null && !existing.cls().equals(definition.cls())) {
            logger.error("Duplicate bean identifier '{}' for types {} and {}", identifier, existing.cls().getName(), definition.cls().getName());
            throw new DuplicateBeanIdentifierException(
                    "DI error: duplicate bean identifier '" + identifier + "' for beans '" + existing.cls().getName() +
                            "' and '" + definition.cls().getName() + "'. Identifiers must be unique."
            );
        }

        beanRegistry.put(identifier, supplier);
        definitions.put(identifier, definition);
        registerTypeHierarchy(definition.cls(), identifier);
        logger.trace("Bean '{}' registered successfully", identifier);
    }

    private void registerTypeHierarchy(Class<?> cls, String identifier) {
        if (cls == null || cls == Object.class) return;
        addTypeMapping(cls, identifier);
        for (Class<?> iface : cls.getInterfaces()) {
            registerTypeHierarchy(iface, identifier);
        }
        registerTypeHierarchy(cls.getSuperclass(), identifier);
    }

    public boolean containsIdentifier(String identifier) {
        boolean contains = beanRegistry.containsKey(identifier);
        logger.trace("containsIdentifier('{}') -> {}", identifier, contains);
        return contains;
    }

    public Object getInstance(String identifier) {
        logger.debug("Resolving bean instance by identifier '{}'", identifier);
        Supplier<?> supplier = beanRegistry.get(identifier);
        if (supplier == null) {
            logger.warn("No bean registered with identifier '{}'", identifier);
            throw new UnregisteredDependencyException(
                    "DI error: no bean registered with identifier '" + identifier + "'."
            );
        }
        Object bean = supplier.get();
        logger.trace("Resolved identifier '{}' to instance of {}", identifier, bean == null ? "null" : bean.getClass().getName());
        return bean;
    }

    public <T> T getInstance(String identifier, Class<T> expectedType) {
        logger.debug("Resolving bean instance by identifier '{}' as type {}", identifier, expectedType.getName());
        Object bean = getInstance(identifier);
        if (!expectedType.isInstance(bean)) {
            logger.warn("Bean '{}' resolved to {}, not assignable to {}", identifier, bean.getClass().getName(), expectedType.getName());
            throw new UnregisteredDependencyException(
                    "DI error: bean identifier '" + identifier + "' resolves to type '" + bean.getClass().getName() +
                            "', not assignable to requested type '" + expectedType.getName() + "'."
            );
        }
        logger.trace("Bean '{}' successfully cast to {}", identifier, expectedType.getName());
        return expectedType.cast(bean);
    }

    public <T> T getInstance(Class<T> cls) {
        logger.debug("Resolving bean instance by type {}", cls.getName());
        List<String> identifiers = typeIndex.getOrDefault(cls, Collections.emptyList());
        if (identifiers.isEmpty()) {
            logger.warn("No bean registered for requested type '{}'", cls.getName());
            throw new UnregisteredDependencyException(
                    "DI error: no bean registered for requested type '" + cls.getName() + "'."
            );
        }

        if (identifiers.size() > 1) {
            logger.warn("Ambiguous bean request for type '{}' with identifiers {}", cls.getName(), identifiers);
            String candidates = identifiers.stream()
                    .map(id -> definitions.get(id).getName() + " as '" + id + "'")
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new AmbiguousBeanException(
                    "DI error: multiple beans registered for requested type '" + cls.getName() + "': [" + candidates +
                            "]. Use getInstance(String, Class) with a qualifier identifier."
            );
        }

        return getInstance(identifiers.getFirst(), cls);
    }

    public Set<String> getBeanIdentifiers() {
        logger.trace("Returning {} registered bean identifier(s)", definitions.size());
        return Collections.unmodifiableSet(definitions.keySet());
    }

    private void addTypeMapping(Class<?> type, String identifier) {
        List<String> identifiers = typeIndex.computeIfAbsent(type, _ -> new ArrayList<>());
        if (!identifiers.contains(identifier)) {
            identifiers.add(identifier);
            logger.trace("Mapped type {} to bean identifier '{}'", type.getName(), identifier);
        }
    }

    private ScopeType resolveScope(Class<?> cls) {
        if (cls.isAnnotationPresent(Scope.class)) {
            return cls.getAnnotation(Scope.class).value();
        }
        for (var annotation : cls.getAnnotations()) {
            var type = annotation.annotationType();
            if (type.isAnnotationPresent(Scope.class)) {
                return type.getAnnotation(Scope.class).value();
            }
        }
        logger.trace("Defaulting scope to SINGLETON for {}", cls.getName());
        return ScopeType.SINGLETON;
    }
}
