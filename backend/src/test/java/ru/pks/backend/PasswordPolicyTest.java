package ru.pks.backend;

import org.junit.jupiter.api.Test;
import ru.pks.backend.error.BadRequestException;
import ru.pks.backend.security.PasswordPolicy;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test void rejectsWeakAndTooLongPasswords() {
        assertThrows(BadRequestException.class, () -> policy.validate("short", "user@company.ru"));
        assertThrows(BadRequestException.class, () -> policy.validate("password123456", "user@company.ru"));
        assertThrows(BadRequestException.class, () -> policy.validate("Admin12345678!", "admin@company.ru"));
        assertThrows(BadRequestException.class, () -> policy.validate("A1!" + "я".repeat(36), "user@company.ru"));
        assertDoesNotThrow(() -> policy.validate("Strong12345!Pass", "user@company.ru"));
    }
}
