package io.github.youssefrashidy.Context.miniProject.fixtures.broken.circular;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;

@Component
public class BeanB {
    @Inject
    public BeanB(BeanA a) {}
}
