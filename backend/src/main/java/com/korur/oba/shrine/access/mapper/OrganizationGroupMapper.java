package com.korur.oba.shrine.access.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.mapper.TextMapper;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupCreateRequest;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupUpdateRequest;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupMemberResponse;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupResponse;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.mapper.UserFullNameMapper;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Maps organization groups and their members between requests, entities and responses. The full name of
 * a member comes from {@link UserFullNameMapper}, so every platform response labels a user the same way.
 * Names and descriptions are trimmed by {@link TextMapper#trimText(String)}, the rule the group service
 * also checks a new name with, so the checked name and the stored name are the same.
 *
 * The organization and the type of a group are left to the group service: a request never chooses them,
 * because only the organization's own setup creates the {@link com.korur.oba.shrine.access.enums.OrganizationGroupType#EVERYONE}
 * group.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        uses = {UserFullNameMapper.class, TextMapper.class})
public interface OrganizationGroupMapper {

    /**
     * Maps a create request to a new custom group without its organization, with a trimmed name and
     * description.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trimText")
    @Mapping(target = "description", source = "description", qualifiedByName = "trimText")
    OrganizationGroup toEntity(OrganizationGroupCreateRequest request);

    /**
     * Applies an update request to a group as a full update, with a trimmed name and description: a null
     * description clears the stored one.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trimText")
    @Mapping(target = "description", source = "description", qualifiedByName = "trimText")
    void updateEntity(@MappingTarget OrganizationGroup group, OrganizationGroupUpdateRequest request);

    /**
     * Maps a group to its response with the values the group service counts and collects.
     *
     * @param group       the stored group
     * @param memberCount the number of members of the group
     * @param apps        the apps granted to the group, in app order
     * @return the group response
     */
    @Mapping(target = "memberCount", source = "memberCount")
    @Mapping(target = "apps", source = "apps")
    OrganizationGroupResponse toResponse(OrganizationGroup group, long memberCount, List<App> apps);

    /**
     * Maps a member of the organization to a member of its Everyone group, which has no member rows.
     *
     * @param member the organization member
     * @return the group member response
     */
    @Mapping(target = "fullName", source = "member", qualifiedByName = "toMemberFullName")
    OrganizationGroupMemberResponse toMemberResponse(OrganizationMemberResponse member);

    /**
     * Maps a member row of a custom group with the user's role in the organization.
     *
     * @param groupMember the member row
     * @param role        the user's role in the organization, or null when the user is no longer a member
     * @return the group member response
     */
    @Mapping(target = "userId", source = "groupMember.user.id")
    @Mapping(target = "fullName", source = "groupMember.user", qualifiedByName = "toUserFullName")
    @Mapping(target = "email", source = "groupMember.user.email")
    @Mapping(target = "role", source = "role")
    OrganizationGroupMemberResponse toGroupMemberResponse(OrganizationGroupMember groupMember,
                                                          OrganizationMemberRole role);
}
