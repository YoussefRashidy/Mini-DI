package io.github.youssefrashidy.context.miniProject.fixtures.broken.primitive;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;

@Component
public class ServerConfig {

    @Inject
    public ServerConfig(int port) {}
}
