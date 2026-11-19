package ru.pks.backend.dto;

import jakarta.validation.constraints.*;
import ru.pks.backend.domain.BookingStatus;
import ru.pks.backend.domain.Urgency;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BookingDtos {
    private BookingDtos() { }

    public record SaveRequest(
            @NotBlank @Size(max = 100) String code,
            @NotNull UUID employeeId,
            @Min(1) @Max(50) int spot,
            @NotNull LocalDate date,
            @NotNull BookingStatus status,
            @NotBlank @Size(max = 200) String purpose,
            boolean guest,
            @Size(max = 20) Set<@NotBlank @Size(max = 50) String> tags,
            @NotNull @PositiveOrZero Long version) { }

    public record Response(UUID id, long legacyId, String code, UUID employeeId,
            String employeeName, String employeeEmail, int spot, LocalDate date,
            BookingStatus status, String purpose, boolean guest, Set<String> tags,
            Urgency urgency, boolean archived, Instant createdAt, Instant updatedAt, long version) { }

    public record VersionRequest(@NotNull @PositiveOrZero Long version) { }
    public enum BulkAction { ARCHIVE, RESTORE, STATUS, ADD_TAG, REMOVE_TAG }
    public record BulkRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> ids,
            @NotNull BulkAction action, BookingStatus status, @Size(max = 50) String tag) { }
    public record BulkResult(List<UUID> successful, List<BulkError> errors) { }
    public record BulkError(UUID id, String message) { }
}
