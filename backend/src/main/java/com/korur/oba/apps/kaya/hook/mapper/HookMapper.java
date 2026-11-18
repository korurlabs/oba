package com.korur.oba.apps.kaya.hook.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.hook.dto.response.HookDetailResponse;
import com.korur.oba.apps.kaya.hook.dto.response.HookListResponse;
import com.korur.oba.apps.kaya.hook.entity.Hook;

/**
 * Maps {@link Hook}s with their size and content to {@link HookListResponse}s and
 * {@link HookDetailResponse}s.
 */
@Mapper(componentModel = "spring")
public interface HookMapper {

    @Mapping(target = "id", source = "hook.id")
    @Mapping(target = "versionId", source = "hook.versionId")
    @Mapping(target = "path", source = "hook.relativePath")
    @Mapping(target = "executable", source = "hook.executable")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    HookListResponse toListResponse(Hook hook, Integer sizeBytes);

    @Mapping(target = "id", source = "hook.id")
    @Mapping(target = "versionId", source = "hook.versionId")
    @Mapping(target = "path", source = "hook.relativePath")
    @Mapping(target = "executable", source = "hook.executable")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "hook.createdAt")
    @Mapping(target = "updatedAt", source = "hook.updatedAt")
    HookDetailResponse toDetailResponse(Hook hook, Integer sizeBytes, String content);
}
