package ru.pks.backend.error;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
