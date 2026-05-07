package io.github.youssefrashidy.Context;

public sealed interface BeanDefinition permits ComponentBeanDefinition , MethodBeanDefinition , DependencyBeanDefinition {
	String getName();
	Class<?> cls();
	String identifier();
}
