package com.korur.oba.shrine.invitation.mapper;

// 1. Framework-level
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;
import com.korur.oba.shrine.invitation.entity.Invitation;

/**
 * MapStruct mapper for Invitation entity to response DTOs.
 */
@Mapper(componentModel = "spring")
public interface InvitationMapper {

    @Mapping(source = "organization.id", target = "organizationId")
    @Mapping(source = "organization.name", target = "organizationName")
    @Mapping(source = "invitedBy.email", target = "invitedByEmail")
    InvitationResponse toResponse(Invitation invitation);
}
