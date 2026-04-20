package io.github.youssefrashidy.Context.apitest.duplicate;

import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Qualifier;

public final class DuplicateIdentifierScanAnchor {
    private DuplicateIdentifierScanAnchor() {
    }
}

interface DuplicateContract {
}

@Component
@Qualifier("dup")
class DuplicateBeanA implements DuplicateContract {
}

@Component
@Qualifier("dup")
class DuplicateBeanB implements DuplicateContract {
}

