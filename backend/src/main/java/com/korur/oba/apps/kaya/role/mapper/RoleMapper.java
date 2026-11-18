package com.korur.oba.apps.kaya.role.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.role.dto.response.RoleDetailResponse;
import com.korur.oba.apps.kaya.role.dto.response.RoleListResponse;
import com.korur.oba.apps.kaya.role.entity.Role;

/**
 * Maps {@link Role}s with their size and content to {@link RoleListResponse}s and
 * {@link RoleDetailResponse}s.
 */
@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "id", source = "role.id")
    @Mapping(target = "versionId", source = "role.versionId")
    @Mapping(target = "path", source = "role.relativePath")
    @Mapping(target = "title", source = "role.title")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    RoleListResponse toListResponse(Role role, Integer sizeBytes);

    @Mapping(target = "id", source = "role.id")
    @Mapping(target = "versionId", source = "role.versionId")
    @Mapping(target = "path", source = "role.relativePath")
    @Mapping(target = "title", source = "role.title")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "role.createdAt")
    @Mapping(target = "updatedAt", source = "role.updatedAt")
    RoleDetailResponse toDetailResponse(Role role, Integer sizeBytes, String content);
}
