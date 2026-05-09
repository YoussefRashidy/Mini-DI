package io.github.youssefrashidy.context.miniProject.fixtures.proto;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Scope;
import io.github.youssefrashidy.annotations.ScopeType;

import java.util.UUID;

@Component
@Scope(ScopeType.PROTOTYPE)
public class SessionFactory {

    private final String id = UUID.randomUUID().toString();

    public String getId() { return id; }
}
