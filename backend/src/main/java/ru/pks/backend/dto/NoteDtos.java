package ru.pks.backend.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class NoteDtos {
    private NoteDtos() { }

    public record SaveRequest(@NotBlank @Size(max = 4000) String body, boolean pinned,
            @NotNull @PositiveOrZero Long version) { }
    public record Response(UUID id, UUID authorId, String authorName, String body, boolean pinned,
            Instant createdAt, Instant updatedAt, long version) { }
}
