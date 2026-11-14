package com.softwarelecture.ticket.domain.exception;

public class DomainException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Kind { VALIDATION, NOT_FOUND, CONFLICT }

    private final Kind kind;

    public DomainException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
