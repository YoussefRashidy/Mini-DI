package io.github.youssefrashidy.Context;

public record AnnotationBeanDefinition(Class<?> cls , String identifier) implements BeanDefinition {
	@Override
	public String getName() {
		return identifier;
	}
}
