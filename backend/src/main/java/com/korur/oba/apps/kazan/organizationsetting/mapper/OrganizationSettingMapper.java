package com.korur.oba.apps.kazan.organizationsetting.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.organizationsetting.dto.response.OrganizationSettingResponse;
import com.korur.oba.apps.kazan.organizationsetting.entity.OrganizationSetting;

/**
 * Maps organization settings entities to DTOs.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface OrganizationSettingMapper {

    /**
     * Maps organization settings to their response DTO.
     */
    OrganizationSettingResponse toResponse(OrganizationSetting organizationSetting);
}
