package io.github.youssefrashidy.Context.miniProject.fixtures.broken.primitive;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;

@Component
public class ServerConfig {

    @Inject
    public ServerConfig(int port) {}
}
