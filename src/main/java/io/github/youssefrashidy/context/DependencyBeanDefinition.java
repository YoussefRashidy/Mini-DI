package io.github.youssefrashidy.context;

import io.github.youssefrashidy.annotations.ScopeType;

public record DependencyBeanDefinition(Class<?> cls , ScopeType scope , String identifier) implements BeanDefinition {
    @Override
    public String getName() {
        return identifier;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BeanDefinition other && identifier.equals(other.identifier());
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }
}
