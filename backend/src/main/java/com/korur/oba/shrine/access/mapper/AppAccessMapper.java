package com.korur.oba.shrine.access.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.shrine.access.dto.request.AppGrantCreateRequest;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.dto.response.MemberAppAccessResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.AppGrantTargetType;
import com.korur.oba.shrine.identity.mapper.UserFullNameMapper;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;

/**
 * Maps app grants between requests, entities and responses, and the effective apps of a member to the
 * member access response.
 *
 * The organization, the target user or group and the granting user of a grant are left to the grant
 * service, which checks that each one belongs to the organization. The full name of a user comes from
 * {@link UserFullNameMapper}, so every platform response labels a user the same way.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        uses = UserFullNameMapper.class)
public interface AppAccessMapper {

    /**
     * Maps a create request to a new grant that carries only its app.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "grantedBy", ignore = true)
    AppGrant toEntity(AppGrantCreateRequest request);

    /**
     * Maps a grant to its response, with the user fields for a user grant and the group fields for a group
     * grant.
     *
     * @param grant the stored grant
     * @return the grant response
     */
    @Mapping(target = "targetType", source = "grant", qualifiedByName = "toTargetType")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userFullName", source = "user", qualifiedByName = "toUserFullName")
    @Mapping(target = "groupId", source = "group.id")
    @Mapping(target = "groupName", source = "group.name")
    @Mapping(target = "groupType", source = "group.type")
    AppGrantResponse toResponse(AppGrant grant);

    /**
     * Maps an organization member with the apps the member can open.
     *
     * @param member the organization member
     * @param apps   the apps the member can open, in app order
     * @return the member access response
     */
    @Mapping(target = "userId", source = "member.userId")
    @Mapping(target = "fullName", source = "member", qualifiedByName = "toMemberFullName")
    @Mapping(target = "role", source = "member.role")
    @Mapping(target = "apps", source = "apps")
    MemberAppAccessResponse toMemberAccessResponse(OrganizationMemberResponse member, List<App> apps);

    /**
     * Derives the target type of a grant: a grant with a user targets that user, any other grant a group.
     *
     * @param grant the grant, may be null
     * @return the target type, or null when the grant is null
     */
    @Named("toTargetType")
    default AppGrantTargetType toTargetType(AppGrant grant) {
        if (grant == null) {
            return null;
        }
        return grant.getUser() != null ? AppGrantTargetType.USER : AppGrantTargetType.GROUP;
    }
}
