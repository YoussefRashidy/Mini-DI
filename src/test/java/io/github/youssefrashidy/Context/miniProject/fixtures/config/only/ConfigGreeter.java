package io.github.youssefrashidy.Context.miniProject.fixtures.config.only;

public class ConfigGreeter {
    private final ConfigGreetingService greetingService;

    public ConfigGreeter(ConfigGreetingService greetingService) {
        this.greetingService = greetingService;
    }

    public String message() {
        return greetingService.message();
    }
}

