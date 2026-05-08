package io.github.youssefrashidy.Exceptions;

public class BeanInstantiationException extends RuntimeException {
    public BeanInstantiationException(Throwable cause) {
        super("DI error: bean instantiation failed — " + cause.getMessage(), cause);
    }
}