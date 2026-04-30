package io.github.youssefrashidy.Context.miniProject.apitest.prototype.invalid;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;

import java.util.function.Supplier;

public final class InvalidPrototypeSupplierScanAnchor {
    private InvalidPrototypeSupplierScanAnchor() {
    }
}

@Component
class StableSingletonService {
}

@Component
class InvalidSupplierConsumer {
    @Inject
    InvalidSupplierConsumer(Supplier<StableSingletonService> supplier) {
    }
}

