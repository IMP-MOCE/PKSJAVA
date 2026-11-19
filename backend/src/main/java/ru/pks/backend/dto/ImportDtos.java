package ru.pks.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ImportDtos {
    private ImportDtos() { }

    public enum Mode { NEW_ONLY, UPSERT }
    public record RowError(int row, String message) { }
    public record Preview(UUID previewId, int validRows, int invalidRows, List<RowError> errors, Instant expiresAt) { }
    public record ApplyRequest(@NotNull UUID previewId) { }
    public record Applied(int successful, List<RowError> errors) { }
}
