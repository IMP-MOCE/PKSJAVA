package ru.pks.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import ru.pks.backend.domain.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Size(max = 128) String temporaryPassword,
            @NotEmpty Set<@jakarta.validation.constraints.NotNull Role> roles) {
    }

    public record UpdateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 160) String displayName,
            boolean enabled,
            @NotEmpty Set<@jakarta.validation.constraints.NotNull Role> roles,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long version) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 128) String temporaryPassword) {
    }

    public record UserResponse(
            UUID id,
            String email,
            String displayName,
            boolean enabled,
            boolean passwordChangeRequired,
            Set<Role> roles,
            Instant lockedUntil,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
