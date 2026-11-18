package com.korur.oba.shrine.identity.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.dto.request.embedded.UserProfileEmbeddedUpdateRequest;
import com.korur.oba.shrine.identity.dto.response.embedded.UserOrganizationEmbeddedResponse;
import com.korur.oba.shrine.identity.dto.response.embedded.UserProfileEmbeddedDetailResponse;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface UserEmbeddedMapper {

    @Named("toProfileUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    UserProfile toProfile(UserProfileEmbeddedUpdateRequest request);

    @Named("toProfileDetail")
    UserProfileEmbeddedDetailResponse toDetail(UserProfile profile);

    /**
     * Maps the ACTIVE memberships of a user to the organizations of the user, each with the apps the user
     * can open there. The caller reads the apps of every membership in one step, through
     * {@link com.korur.oba.shrine.access.service.AppAccessQueryService#listAccessibleAppsByOrganization}, so the mapping itself reads nothing.
     *
     * @param memberships        the memberships of the user, may be null
     * @param appsByOrganization the apps by organization id; an organization left out has no app
     * @return the organizations, or null for null memberships
     */
    @Named("toOrganizationMemberships")
    default List<UserOrganizationEmbeddedResponse> toOrganizationMemberships(List<OrganizationMember> memberships,
            @Context Map<UUID, List<App>> appsByOrganization) {
        if (memberships == null) {
            return null;
        }
        return memberships.stream()
                .filter((OrganizationMember member) -> member.getStatus() == OrganizationMemberStatus.ACTIVE)
                .map((OrganizationMember member) -> toOrganizationMembership(member,
                        appsByOrganization.getOrDefault(member.getOrganization().getId(), List.of())))
                .toList();
    }

    /**
     * Maps one membership with the apps the user can open in its organization.
     *
     * @param member the membership
     * @param apps   the apps, in app order
     * @return the organization of the membership
     */
    @Mapping(source = "member.organization.id", target = "id")
    @Mapping(source = "member.organization.name", target = "name")
    @Mapping(source = "member.role", target = "role")
    @Mapping(source = "member.organization.status", target = "organizationStatus")
    @Mapping(source = "apps", target = "apps")
    UserOrganizationEmbeddedResponse toOrganizationMembership(OrganizationMember member, List<App> apps);
}
