package ru.pks.backend.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class EmployeeDtos {
    private EmployeeDtos() { }

    public record SaveRequest(@NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 100) String email, boolean active,
            @NotNull @PositiveOrZero Long version) { }
    public record Response(UUID id, long legacyId, String name, String email, boolean active, long version) { }
}
