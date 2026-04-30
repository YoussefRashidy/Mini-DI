package io.github.youssefrashidy.Context.miniProject.fixtures.config.only;

import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Configuration;
import io.github.youssefrashidy.annotations.ScopeType;

@Configuration
public class ConfigOnlyConfiguration {
    @Bean("configGreeting")
    public ConfigGreetingService greetingService() {
        return new ConfigGreetingService("hello-config");
    }

    @Bean("")
    public ConfigGreeter greeter() {
        return new ConfigGreeter(new ConfigGreetingService("hello-config"));
    }

    @Bean(value = "configCounter", scope = ScopeType.PROTOTYPE)
    public ConfigCounter counter() {
        return new ConfigCounter();
    }
}
