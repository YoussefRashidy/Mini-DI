package io.github.youssefrashidy.context;

import java.util.List;
import java.util.Map;

public record ConfigurationContext(Map<Class<?>, Object> proxies , List<MethodBeanDefinition> beanDefinitions) {
}
