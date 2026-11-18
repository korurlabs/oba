package com.korur.oba.apps.kaya.guardrail.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.guardrail.dto.response.GuardrailDetailResponse;
import com.korur.oba.apps.kaya.guardrail.dto.response.GuardrailListResponse;
import com.korur.oba.apps.kaya.guardrail.entity.Guardrail;
import com.korur.oba.apps.kaya.guardrail.enums.GuardrailEnforcement;

/**
 * Maps {@link Guardrail}s with their path, enforcement, size and content to {@link GuardrailListResponse}s
 * and {@link GuardrailDetailResponse}s.
 */
@Mapper(componentModel = "spring")
public interface GuardrailMapper {

    @Mapping(target = "id", source = "guardrail.id")
    @Mapping(target = "versionId", source = "guardrail.versionId")
    @Mapping(target = "path", source = "path")
    @Mapping(target = "name", source = "guardrail.name")
    @Mapping(target = "category", source = "guardrail.category")
    @Mapping(target = "description", source = "guardrail.description")
    @Mapping(target = "deny", source = "guardrail.deny")
    @Mapping(target = "enforcement", source = "enforcement")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    GuardrailListResponse toListResponse(Guardrail guardrail, String path, GuardrailEnforcement enforcement,
            Integer sizeBytes);

    @Mapping(target = "id", source = "guardrail.id")
    @Mapping(target = "versionId", source = "guardrail.versionId")
    @Mapping(target = "path", source = "path")
    @Mapping(target = "name", source = "guardrail.name")
    @Mapping(target = "category", source = "guardrail.category")
    @Mapping(target = "description", source = "guardrail.description")
    @Mapping(target = "deny", source = "guardrail.deny")
    @Mapping(target = "enforcement", source = "enforcement")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "guardrail.createdAt")
    @Mapping(target = "updatedAt", source = "guardrail.updatedAt")
    GuardrailDetailResponse toDetailResponse(Guardrail guardrail, String path, GuardrailEnforcement enforcement,
            Integer sizeBytes, String content);
}
