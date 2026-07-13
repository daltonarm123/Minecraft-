package com.community.servercore.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID eventId,
        AuditEventType type,
        Instant occurredAt,
        UUID actorId,
        String actorName,
        String message,
        Map<String, String> attributes) {

    public AuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        actorName = Objects.requireNonNullElse(actorName, "system").trim();
        message = Objects.requireNonNullElse(message, "");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static AuditEvent system(AuditEventType type, Instant now, String message) {
        return new AuditEvent(UUID.randomUUID(), type, now, null, "system", message, Map.of());
    }

    public static AuditEvent actor(
            AuditEventType type,
            Instant now,
            UUID actorId,
            String actorName,
            String message,
            Map<String, String> attributes) {
        return new AuditEvent(
                UUID.randomUUID(),
                type,
                now,
                Objects.requireNonNull(actorId, "actorId"),
                actorName,
                message,
                attributes);
    }
}
