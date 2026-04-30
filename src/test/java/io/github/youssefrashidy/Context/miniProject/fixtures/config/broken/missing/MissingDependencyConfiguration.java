package io.github.youssefrashidy.Context.miniProject.fixtures.config.broken.missing;

import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Configuration;

@Configuration
public class MissingDependencyConfiguration {
    @Bean("missingConfigBean")
    public MissingConfigBean missingBean(MissingDependency dependency) {
        return new MissingConfigBean(dependency);
    }
}

