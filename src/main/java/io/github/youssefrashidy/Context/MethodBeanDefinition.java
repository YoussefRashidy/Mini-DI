package io.github.youssefrashidy.Context;

import io.github.youssefrashidy.annotations.ScopeType;

import java.lang.reflect.Method;

public record MethodBeanDefinition(Class<?> cls , Method beanMethod, Object proxy , String identifier, ScopeType scope) implements BeanDefinition {
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
