package ru.pks.backend.error;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
