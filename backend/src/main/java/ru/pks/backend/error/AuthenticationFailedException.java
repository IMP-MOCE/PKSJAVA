package ru.pks.backend.error;

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() { super("Неверные учётные данные или доступ временно заблокирован"); }
}
