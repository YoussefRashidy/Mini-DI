package io.github.youssefrashidy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Scope(ScopeType.SINGELTON)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Singelton {
}
