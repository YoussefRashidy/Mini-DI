package io.github.youssefrashidy.Context.miniProject.fixtures.config.broken.duplicate;

import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Configuration;

@Configuration
public class DuplicateBeanConfiguration {
    @Bean("duplicateBean")
    public DuplicateBean first() {
        return new DuplicateBean("first");
    }

    @Bean("duplicateBean")
    public DuplicateBean second() {
        return new DuplicateBean("second");
    }
}

