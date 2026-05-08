package io.github.youssefrashidy.Context;

import io.github.youssefrashidy.annotations.ScopeType;

public sealed interface BeanDefinition permits ComponentBeanDefinition , MethodBeanDefinition , DependencyBeanDefinition {
	String getName();
	Class<?> cls();
	String identifier();
	ScopeType scope();
}
