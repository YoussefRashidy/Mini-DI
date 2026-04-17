package io.github.youssefrashidy.Exceptions;

public class UnregisteredDependencyException extends RuntimeException{
    public UnregisteredDependencyException() {
    }

    public UnregisteredDependencyException(String message) {
        super(message);
    }

    public UnregisteredDependencyException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnregisteredDependencyException(Throwable cause) {
        super(cause);
    }

    public UnregisteredDependencyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
