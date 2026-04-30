package io.github.youssefrashidy.Context;

public sealed interface BeanDefinition permits AnnotationBeanDefinition , MethodBeanDefinition {
	String getName();
	Class<?> cls();
	String identifier();
}
