package io.github.youssefrashidy.exceptions;

public class BeanInstantiationException extends RuntimeException {
    public BeanInstantiationException(Throwable cause) {
        super("DI error: bean instantiation failed — " + cause.getMessage(), cause);
    }
}