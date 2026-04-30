package io.github.youssefrashidy.Context;

public record AnnotationBeanDefinition(Class<?> cls , String identifier) implements BeanDefinition {
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
