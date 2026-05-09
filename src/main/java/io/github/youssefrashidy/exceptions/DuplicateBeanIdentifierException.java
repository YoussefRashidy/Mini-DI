package io.github.youssefrashidy.exceptions;

public class DuplicateBeanIdentifierException extends RuntimeException{
    public DuplicateBeanIdentifierException() {
        super("DI error: duplicate bean identifier detected. Bean identifiers must be unique.");
    }

    public DuplicateBeanIdentifierException(String message) {
        super(message);
    }

    public DuplicateBeanIdentifierException(Throwable cause) {
        super(cause);
    }

    public DuplicateBeanIdentifierException(String message, Throwable cause) {
        super(message, cause);
    }

    public DuplicateBeanIdentifierException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
