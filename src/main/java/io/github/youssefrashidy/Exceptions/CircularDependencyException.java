package io.github.youssefrashidy.Exceptions;

public class CircularDependencyException extends RuntimeException{
    public CircularDependencyException() {
        super("DI error: circular dependency detected while building the bean initialization order.");
    }

    public CircularDependencyException(String message) {
        super(message);
    }

    public CircularDependencyException(String message, Throwable cause) {
        super(message, cause);
    }

    public CircularDependencyException(Throwable cause) {
        super(cause);
    }

    public CircularDependencyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
