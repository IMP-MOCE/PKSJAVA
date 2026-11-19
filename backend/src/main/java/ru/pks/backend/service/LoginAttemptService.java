package ru.pks.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.pks.backend.domain.UserAccount;
import ru.pks.backend.repository.UserAccountRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private final UserAccountRepository users;
    private final AuditService audit;

    public LoginAttemptService(UserAccountRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            int failures = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failures);
            if (failures >= MAX_FAILURES) {
                int multiplier = Math.min(8, failures - MAX_FAILURES + 1);
                user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(5L * multiplier)));
            }
            audit.record("LOGIN_FAILED", "USER", user.getId(), "Неудачная попытка входа",
                    java.util.Map.of("email", user.getEmail()));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        });
    }

    public boolean isLocked(UserAccount user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }
}
