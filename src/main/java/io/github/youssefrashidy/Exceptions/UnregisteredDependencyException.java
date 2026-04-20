package io.github.youssefrashidy.Exceptions;

public class UnregisteredDependencyException extends RuntimeException{
    public UnregisteredDependencyException() {
        super("DI error: unregistered dependency. A required bean is missing from the application context.");
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
