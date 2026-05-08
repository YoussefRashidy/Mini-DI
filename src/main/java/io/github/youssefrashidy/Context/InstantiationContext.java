package io.github.youssefrashidy.Context;

public record InstantiationContext(
        ScanMap scanMap,
        ConfigurationContext configurationContext,
        BeanContainer beanContainer
) {
}

