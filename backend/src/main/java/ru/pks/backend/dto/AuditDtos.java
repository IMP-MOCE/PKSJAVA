package ru.pks.backend.dto;

import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditResponse(
            UUID id,
            UUID actorId,
            String actorEmail,
            String eventType,
            String entityType,
            UUID entityId,
            String summary,
            String correlationId,
            String ipAddress,
            Instant occurredAt,
            String detailsJson) {
    }
}
