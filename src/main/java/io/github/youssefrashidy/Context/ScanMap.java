package io.github.youssefrashidy.Context;

import java.util.List;
import java.util.Map;

public record ScanMap(Map<Class<?>, List<ComponentBeanDefinition>> resolveMap , List<ComponentBeanDefinition> components , List<Class<?>> configurationClasses ) {
}
