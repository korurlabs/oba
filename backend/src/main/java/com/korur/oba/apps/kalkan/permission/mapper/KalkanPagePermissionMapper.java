package com.korur.oba.apps.kalkan.permission.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kalkan.permission.dto.response.KalkanPagePermissionResponse;
import com.korur.oba.apps.kalkan.permission.entity.KalkanPagePermission;

/**
 * Maps a {@link KalkanPagePermission} with the display name of its subject to a
 * {@link KalkanPagePermissionResponse}. Every subject is a member until organization groups
 * exist, so the group id is left empty.
 */
@Mapper(componentModel = "spring")
public interface KalkanPagePermissionMapper {

    @Mapping(target = "id", source = "permission.id")
    @Mapping(target = "userId", source = "permission.userId")
    @Mapping(target = "groupId", ignore = true)
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "level", source = "permission.level")
    KalkanPagePermissionResponse toResponse(KalkanPagePermission permission, String displayName);
}
