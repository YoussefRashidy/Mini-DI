package io.github.youssefrashidy.context.miniProject.fixtures.core;

import java.util.UUID;

public class SessionFactory {

    private final String id = UUID.randomUUID().toString();

    public String getId() { return id; }
}
