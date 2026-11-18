package com.korur.oba.dojo.audit.dto.request;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for creating audit log entries.
 * Use AuditLogMapper.toRequest() to construct instances -- never call the constructor directly.
 */
public record AuditLogRequest(
        UUID organizationId,
        UUID actorId,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> oldValues,
        Map<String, Object> newValues
) {}
