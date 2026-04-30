package io.github.youssefrashidy.Exceptions;

public class BeanMethodDependencyException extends RuntimeException{
    public BeanMethodDependencyException() {
    }

    public BeanMethodDependencyException(String message) {
        super(message);
    }

    public BeanMethodDependencyException(String message, Throwable cause) {
        super(message, cause);
    }

    public BeanMethodDependencyException(Throwable cause) {
        super(cause);
    }

    public BeanMethodDependencyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
