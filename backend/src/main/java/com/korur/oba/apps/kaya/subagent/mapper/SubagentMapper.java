package com.korur.oba.apps.kaya.subagent.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.subagent.dto.response.SubagentDetailResponse;
import com.korur.oba.apps.kaya.subagent.dto.response.SubagentListResponse;
import com.korur.oba.apps.kaya.subagent.entity.Subagent;

/**
 * Maps {@link Subagent}s with their size and content to {@link SubagentListResponse}s and
 * {@link SubagentDetailResponse}s.
 */
@Mapper(componentModel = "spring")
public interface SubagentMapper {

    @Mapping(target = "id", source = "subagent.id")
    @Mapping(target = "versionId", source = "subagent.versionId")
    @Mapping(target = "path", source = "subagent.relativePath")
    @Mapping(target = "name", source = "subagent.name")
    @Mapping(target = "description", source = "subagent.description")
    @Mapping(target = "model", source = "subagent.model")
    @Mapping(target = "maxTurns", source = "subagent.maxTurns")
    @Mapping(target = "tools", source = "subagent.tools")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    SubagentListResponse toListResponse(Subagent subagent, Integer sizeBytes);

    @Mapping(target = "id", source = "subagent.id")
    @Mapping(target = "versionId", source = "subagent.versionId")
    @Mapping(target = "path", source = "subagent.relativePath")
    @Mapping(target = "name", source = "subagent.name")
    @Mapping(target = "description", source = "subagent.description")
    @Mapping(target = "model", source = "subagent.model")
    @Mapping(target = "maxTurns", source = "subagent.maxTurns")
    @Mapping(target = "tools", source = "subagent.tools")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "subagent.createdAt")
    @Mapping(target = "updatedAt", source = "subagent.updatedAt")
    SubagentDetailResponse toDetailResponse(Subagent subagent, Integer sizeBytes, String content);
}
