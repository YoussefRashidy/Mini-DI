package io.github.youssefrashidy.context;

import io.github.youssefrashidy.exceptions.AmbiguousBeanException;
import io.github.youssefrashidy.exceptions.DuplicateBeanIdentifierException;
import io.github.youssefrashidy.exceptions.UnregisteredDependencyException;
import io.github.youssefrashidy.annotations.Scope;
import io.github.youssefrashidy.annotations.ScopeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BeanContainer {
    private final Map<String, Supplier<?>> beanRegistry = new HashMap<>();
    private final Map<String, BeanDefinition> definitions = new HashMap<>();
    private final Map<Class<?>, List<String>> typeIndex = new HashMap<>();

    public void registerBean(Class<?> cls, String identifier, Supplier<?> supplier) {
        registerBean(new ComponentBeanDefinition(cls, resolveScope(cls), identifier), supplier);
    }

    public void registerBean(BeanDefinition definition, Supplier<?> supplier) {
        String identifier = definition.identifier();
        BeanDefinition existing = definitions.get(identifier);
        if (existing != null && !existing.cls().equals(definition.cls())) {
            throw new DuplicateBeanIdentifierException(
                    "DI error: duplicate bean identifier '" + identifier + "' for beans '" + existing.cls().getName() +
                            "' and '" + definition.cls().getName() + "'. Identifiers must be unique."
            );
        }

        beanRegistry.put(identifier, supplier);
        definitions.put(identifier, definition);
        registerTypeHierarchy(definition.cls(), identifier);
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
        return beanRegistry.containsKey(identifier);
    }

    public Object getInstance(String identifier) {
        Supplier<?> supplier = beanRegistry.get(identifier);
        if (supplier == null) {
            throw new UnregisteredDependencyException(
                    "DI error: no bean registered with identifier '" + identifier + "'."
            );
        }
        return supplier.get();
    }

    public <T> T getInstance(String identifier, Class<T> expectedType) {
        Object bean = getInstance(identifier);
        if (!expectedType.isInstance(bean)) {
            throw new UnregisteredDependencyException(
                    "DI error: bean identifier '" + identifier + "' resolves to type '" + bean.getClass().getName() +
                            "', not assignable to requested type '" + expectedType.getName() + "'."
            );
        }
        return expectedType.cast(bean);
    }

    public <T> T getInstance(Class<T> cls) {
        List<String> identifiers = typeIndex.getOrDefault(cls, Collections.emptyList());
        if (identifiers.isEmpty()) {
            throw new UnregisteredDependencyException(
                    "DI error: no bean registered for requested type '" + cls.getName() + "'."
            );
        }

        if (identifiers.size() > 1) {
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
        return Collections.unmodifiableSet(definitions.keySet());
    }

    private void addTypeMapping(Class<?> type, String identifier) {
        List<String> identifiers = typeIndex.computeIfAbsent(type, _ -> new ArrayList<>());
        if (!identifiers.contains(identifier)) {
            identifiers.add(identifier);
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
        return ScopeType.SINGLETON;
    }
}
