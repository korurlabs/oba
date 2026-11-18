package com.korur.oba.shrine.organization.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.organization.dto.request.OrganizationUpdateRequest;
import com.korur.oba.shrine.organization.dto.response.OrganizationDetailResponse;
import com.korur.oba.shrine.organization.dto.response.OrganizationListResponse;
import com.korur.oba.shrine.organization.entity.Organization;

/**
 * Maps organization entities to DTOs and applies update requests to entities.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface OrganizationMapper {

    /**
     * Applies an update request to an existing organization entity as a full update.
     * Every request field is written to the entity, so a null optional field clears the stored value.
     * The name is never null here because request validation requires it.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(@MappingTarget Organization organization, OrganizationUpdateRequest request);

    /**
     * Maps an organization entity to a list response DTO.
     */
    OrganizationListResponse toListResponse(Organization organization);

    /**
     * Maps an organization entity to a detail response DTO.
     */
    OrganizationDetailResponse toDetailResponse(Organization organization);
}
