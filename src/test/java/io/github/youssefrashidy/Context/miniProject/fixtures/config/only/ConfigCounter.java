package io.github.youssefrashidy.Context.miniProject.fixtures.config.only;

import java.util.UUID;

public class ConfigCounter {
    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }
}

