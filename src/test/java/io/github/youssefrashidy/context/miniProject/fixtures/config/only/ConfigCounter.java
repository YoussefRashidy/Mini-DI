package io.github.youssefrashidy.context.miniProject.fixtures.config.only;

import java.util.UUID;

public class ConfigCounter {
    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }
}

