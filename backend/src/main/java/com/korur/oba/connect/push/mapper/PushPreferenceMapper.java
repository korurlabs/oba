package com.korur.oba.connect.push.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.push.dto.request.PushPreferenceUpdateRequest;
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.connect.push.entity.PushPreference;

/**
 * Maps push preference entities to DTOs and applies updates to them.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PushPreferenceMapper {

    /**
     * Maps push preferences to their response DTO.
     */
    PushPreferenceResponse toResponse(PushPreference pushPreference);

    /**
     * Applies an update to the preferences as a full update; validation guarantees every switch is
     * set.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    void updateEntity(@MappingTarget PushPreference pushPreference, PushPreferenceUpdateRequest request);
}
