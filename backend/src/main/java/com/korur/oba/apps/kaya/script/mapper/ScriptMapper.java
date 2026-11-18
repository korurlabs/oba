package com.korur.oba.apps.kaya.script.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.script.dto.response.ScriptDetailResponse;
import com.korur.oba.apps.kaya.script.entity.Script;

/**
 * Maps a {@link Script} with its size and content to a {@link ScriptDetailResponse}.
 */
@Mapper(componentModel = "spring")
public interface ScriptMapper {

    @Mapping(target = "id", source = "script.id")
    @Mapping(target = "versionId", source = "script.versionId")
    @Mapping(target = "name", source = "script.name")
    @Mapping(target = "executable", source = "script.executable")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "script.createdAt")
    @Mapping(target = "updatedAt", source = "script.updatedAt")
    ScriptDetailResponse toDetailResponse(Script script, Integer sizeBytes, String content);
}
