package io.github.youssefrashidy.Context.miniProject.fixtures.config.only;

public class ConfigGreetingService {
    private final String message;

    public ConfigGreetingService(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}

