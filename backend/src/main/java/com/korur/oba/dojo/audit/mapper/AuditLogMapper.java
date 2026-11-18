package com.korur.oba.dojo.audit.mapper;

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.dto.request.AuditLogRequest;
import com.korur.oba.dojo.audit.dto.response.AuditLogResponse;
import com.korur.oba.dojo.audit.entity.AuditLog;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);

    /**
     * Creates an AuditLogRequest from individual parameters.
     * MapStruct auto-maps parameter names to record components.
     */
    AuditLogRequest toRequest(UUID organizationId, UUID actorId, String action,
                              String entityType, UUID entityId,
                              Map<String, Object> oldValues, Map<String, Object> newValues);

    /**
     * Maps an AuditLogRequest to an AuditLog entity.
     * actorId in the DTO maps to userId in the entity.
     */
    @Mapping(source = "actorId", target = "userId")
    AuditLog toEntity(AuditLogRequest request);
}
