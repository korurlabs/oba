package com.korur.oba.shrine.identity.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.dto.request.UserSelfUpdateRequest;
import com.korur.oba.shrine.identity.dto.response.UserDetailResponse;
import com.korur.oba.shrine.identity.dto.response.UserListResponse;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;

@Mapper(componentModel = "spring", uses = {
        UserEmbeddedMapper.class
}, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface UserMapper {

    @Mapping(target = "profile", source = "profile", qualifiedByName = "toProfileUpdate")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "keycloakId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "defaultOrganization", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget User user, UserSelfUpdateRequest request);

    /**
     * Applies the changed name, role and status of an agent user. A null argument leaves that value
     * unchanged, and nothing else of the user is touched: flow, provider and organization of an agent
     * never change.
     *
     * @param agent  the agent user, changed in place
     * @param name   the new name, kept as the profile's first name, or null
     * @param role   the new role, or null
     * @param status the new status, or null
     */
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "profile.firstName", source = "name")
    @Mapping(target = "agentDetail.role", source = "role")
    @Mapping(target = "status", source = "status")
    void updateAgent(@MappingTarget User agent, String name, AgentRole role, UserStatus status);

    @Mapping(source = "profile", target = "profile", qualifiedByName = "toProfileDetail")
    @Mapping(target = "userStatus", source = "status")
    UserListResponse toListResponse(User user);

    /**
     * Maps a user to the detail response without pending invitations.
     *
     * @param user               the user
     * @param appsByOrganization the apps the user can open, by organization id, from
     *                           {@link com.korur.oba.shrine.access.service.AppAccessQueryService#listAccessibleAppsByOrganization}
     * @return the detail response
     */
    @Mapping(source = "profile", target = "profile", qualifiedByName = "toProfileDetail")
    @Mapping(target = "userStatus", source = "status")
    @Mapping(source = "defaultOrganization.id", target = "defaultOrganizationId")
    @Mapping(source = "organizationMemberships", target = "organizations", qualifiedByName = "toOrganizationMemberships")
    @Mapping(target = "pendingInvitations", ignore = true)
    UserDetailResponse toDetailResponse(User user, @Context Map<UUID, List<App>> appsByOrganization);

    /**
     * Maps a user to the detail response with the pending invitations of the user.
     *
     * @param user               the user
     * @param pendingInvitations the pending invitations of the user
     * @param appsByOrganization the apps the user can open, by organization id, from
     *                           {@link com.korur.oba.shrine.access.service.AppAccessQueryService#listAccessibleAppsByOrganization}
     * @return the detail response
     */
    @Mapping(source = "user.profile", target = "profile", qualifiedByName = "toProfileDetail")
    @Mapping(target = "userStatus", source = "user.status")
    @Mapping(source = "user.defaultOrganization.id", target = "defaultOrganizationId")
    @Mapping(source = "user.organizationMemberships", target = "organizations", qualifiedByName = "toOrganizationMemberships")
    UserDetailResponse toDetailResponse(User user, List<InvitationResponse> pendingInvitations,
                                        @Context Map<UUID, List<App>> appsByOrganization);
}
