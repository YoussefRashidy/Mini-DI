package io.github.youssefrashidy.Context;

import java.util.List;
import java.util.Map;

public record ScanMap(Map<Class<?>, List<Class<?>>> resolveMap , List<Class<?>> componentList , List<Class<?>> configurationClasses ) {
}
