package io.github.youssefrashidy.Context;

import java.lang.reflect.Method;

public record MethodBeanDefinition(Class<?> cls , Method beanMethod,Object proxy ,String identifier) implements BeanDefinition {
	@Override
	public String getName() {
		return identifier;
	}
}
