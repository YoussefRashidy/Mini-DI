package io.github.youssefrashidy.Context;

import java.util.List;
import java.util.Map;

public record ConfigurationContext(Map<Class<?>, Object> proxies , List<BeanDefinition> beanDefinitions) {
}
