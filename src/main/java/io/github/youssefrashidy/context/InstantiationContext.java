package io.github.youssefrashidy.context;

public record InstantiationContext(
        ScanMap scanMap,
        ConfigurationContext configurationContext,
        BeanContainer beanContainer
) {
}

