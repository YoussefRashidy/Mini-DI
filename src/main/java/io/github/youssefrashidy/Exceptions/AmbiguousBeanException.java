package io.github.youssefrashidy.Exceptions;

public class AmbiguousBeanException extends RuntimeException{
    public AmbiguousBeanException() {
    }

    public AmbiguousBeanException(String message) {
        super(message);
    }

    public AmbiguousBeanException(String message, Throwable cause) {
        super(message, cause);
    }

    public AmbiguousBeanException(Throwable cause) {
        super(cause);
    }

    public AmbiguousBeanException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
