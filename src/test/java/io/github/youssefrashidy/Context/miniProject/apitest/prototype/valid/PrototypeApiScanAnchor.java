package io.github.youssefrashidy.Context.miniProject.apitest.prototype.valid;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.annotations.Scope;
import io.github.youssefrashidy.annotations.ScopeType;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class PrototypeApiScanAnchor {
    private PrototypeApiScanAnchor() {
    }
}

interface PrototypeTokenService {
    int serial();
}

@Component
@Scope(ScopeType.PROTOTYPE)
class PrototypeTokenServiceImpl implements PrototypeTokenService {
    private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
    private final int serial;

    PrototypeTokenServiceImpl() {
        this.serial = CONSTRUCTOR_CALLS.incrementAndGet();
    }

    static int constructorCalls() {
        return CONSTRUCTOR_CALLS.get();
    }

    @Override
    public int serial() {
        return serial;
    }
}

@Component
class PrototypeConsumer {
    private final Supplier<PrototypeTokenService> tokenSupplier;

    @Inject
    PrototypeConsumer(Supplier<PrototypeTokenService> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
    }

    int nextTokenSerial() {
        return tokenSupplier.get().serial();
    }
}

