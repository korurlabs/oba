package com.korur.oba.dojo.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(

        @Schema(description = "Audit log ID") UUID id,
        @Schema(description = "User ID") UUID userId,
        @Schema(description = "Action") String action,
        @Schema(description = "Entity type") String entityType,
        @Schema(description = "Entity ID") UUID entityId,
        @Schema(description = "Old values") Map<String, Object> oldValues,
        @Schema(description = "New values") Map<String, Object> newValues,
        @Schema(description = "IP address") String ipAddress,
        @Schema(description = "Created at") Instant createdAt
) {}
