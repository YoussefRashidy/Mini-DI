package io.github.youssefrashidy.Context.miniProject.fixtures.core;

import java.util.UUID;

public class SessionFactory {

    private final String id = UUID.randomUUID().toString();

    public String getId() { return id; }
}
