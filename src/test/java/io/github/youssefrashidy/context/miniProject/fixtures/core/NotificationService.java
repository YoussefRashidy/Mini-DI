package io.github.youssefrashidy.context.miniProject.fixtures.core;

import io.github.youssefrashidy.annotations.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class NotificationService {

    private final List<String> sent = new ArrayList<>();

    public void notify(String orderId) {
        sent.add(orderId);
    }

    public List<String> getSent() {
        return Collections.unmodifiableList(sent);
    }
}
