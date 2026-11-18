package com.korur.oba.shrine.organization.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.entity.OrganizationMember;

/**
 * Maps organization membership entities to response DTOs.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface OrganizationMemberMapper {

    /**
     * Maps a membership entity into a response DTO.
     */
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.profile.firstName", target = "firstName")
    @Mapping(source = "user.profile.lastName", target = "lastName")
    OrganizationMemberResponse toResponse(OrganizationMember member);
}
