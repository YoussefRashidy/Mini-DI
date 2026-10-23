package io.github.youssefrashidy.context;

import io.github.youssefrashidy.annotations.ScopeType;

public record ComponentBeanDefinition(Class<?> cls, ScopeType scope, String identifier) implements BeanDefinition {
    @Override
    public String getName() {
        return identifier;
    }

    @Override
    public boolean equals(Object obj) {
        return switch (obj) {
            case ComponentBeanDefinition other     -> identifier.equals(other.identifier()) ;
            case DependencyBeanDefinition other -> identifier.equals(other.identifier()) ;
            case null, default                  -> false;
        };
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }
}
