package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupMemberResponse;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.mapper.OrganizationGroupMapper;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupMemberRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupRepository;
import com.korur.oba.shrine.access.service.OrganizationGroupQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Implements the read operations for organization groups.
 *
 * The members of the Everyone group are derived from the ACTIVE members of the organization, which come
 * from {@link OrganizationMemberQueryService}; a custom group reads its own member rows.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationGroupQueryServiceImpl implements OrganizationGroupQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationGroupQueryServiceImpl.class);

    // The Everyone group first, then by name whatever its case.
    private static final Comparator<OrganizationGroup> GROUP_ORDER = Comparator
            .comparing((OrganizationGroup group) -> group.getType() != OrganizationGroupType.EVERYONE)
            .thenComparing(OrganizationGroup::getName, String.CASE_INSENSITIVE_ORDER);

    // Members by full name, then by email, whatever their case.
    private static final Comparator<OrganizationGroupMemberResponse> MEMBER_ORDER = Comparator
            .comparing(OrganizationGroupMemberResponse::fullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(OrganizationGroupMemberResponse::email, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final OrganizationGroupRepository organizationGroupRepository;
    private final OrganizationGroupMemberRepository organizationGroupMemberRepository;
    private final AppGrantRepository appGrantRepository;
    private final OrganizationGroupMapper organizationGroupMapper;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    @Override
    public List<OrganizationGroupResponse> listGroups(User user, UUID organizationId) {
        List<OrganizationGroup> groups = organizationGroupRepository.findByOrganization_Id(organizationId);
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<App>> appsByGroupId = collectAppsByGroupId(organizationId);
        long activeMemberCount = countActiveMembers(user, organizationId);

        return groups.stream()
                .sorted(GROUP_ORDER)
                .map(group -> organizationGroupMapper.toResponse(
                        group,
                        group.getType() == OrganizationGroupType.EVERYONE
                                ? activeMemberCount
                                : organizationGroupMemberRepository.countByGroup_Id(group.getId()),
                        appsByGroupId.getOrDefault(group.getId(), List.of())))
                .toList();
    }

    @Override
    public List<OrganizationGroupMemberResponse> listGroupMembers(User user, UUID organizationId, UUID groupId) {
        OrganizationGroup group = getGroupEntity(organizationId, groupId);
        List<OrganizationMemberResponse> members =
                organizationMemberQueryService.listOrganizationMembers(user, organizationId);

        if (group.getType() == OrganizationGroupType.EVERYONE) {
            return members.stream()
                    .filter(member -> member.status() == OrganizationMemberStatus.ACTIVE)
                    .map(organizationGroupMapper::toMemberResponse)
                    .sorted(MEMBER_ORDER)
                    .toList();
        }

        // A member row outlives a disabled membership, so the row shows the role whatever the status.
        Map<UUID, OrganizationMemberRole> roleByUserId = members.stream()
                .collect(Collectors.toMap(OrganizationMemberResponse::userId, OrganizationMemberResponse::role));
        return organizationGroupMemberRepository.findByGroup_Id(groupId)
                .stream()
                .map(groupMember -> organizationGroupMapper.toGroupMemberResponse(
                        groupMember, roleByUserId.get(groupMember.getUser().getId())))
                .sorted(MEMBER_ORDER)
                .toList();
    }

    @Override
    public OrganizationGroup getGroupEntity(UUID organizationId, UUID groupId) {
        return organizationGroupRepository.findByIdAndOrganization_Id(groupId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AccessExceptionLogConstant.GROUP_NOT_FOUND,
                            organizationId, groupId);
                    return new NotFoundException(
                            ExceptionDomainAccessError.NOT_GROUP_FOUND,
                            Map.of(
                                    OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                                    AccessConstant.FIELD_GROUP_ID, groupId));
                });
    }

    @Override
    public Set<UUID> findGroupUserIds(OrganizationGroup group) {
        if (group.getType() == OrganizationGroupType.EVERYONE) {
            return organizationMemberQueryService.findActiveUserIdsByRoles(
                    group.getOrganization().getId(), AccessConstant.GRANT_DEPENDENT_ROLES);
        }
        return organizationGroupMemberRepository.findByGroup_Id(group.getId())
                .stream()
                .map(groupMember -> groupMember.getUser().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Collects the apps granted to each group of an organization, in app order, from one read of the
     * organization's grants.
     */
    private Map<UUID, List<App>> collectAppsByGroupId(UUID organizationId) {
        Map<UUID, EnumSet<App>> appSets = new HashMap<>();
        for (AppGrant grant : appGrantRepository.findByOrganization_Id(organizationId)) {
            if (grant.getGroup() == null) {
                continue;
            }
            appSets.computeIfAbsent(grant.getGroup().getId(), groupId -> EnumSet.noneOf(App.class))
                    .add(grant.getApp());
        }
        return appSets.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    /**
     * Counts the ACTIVE members of an organization, who are the members of its Everyone group; the same
     * rule as {@link #listGroupMembers}, so the count and the list agree.
     */
    private long countActiveMembers(User user, UUID organizationId) {
        return organizationMemberQueryService.listOrganizationMembers(user, organizationId)
                .stream()
                .filter(member -> member.status() == OrganizationMemberStatus.ACTIVE)
                .count();
    }
}
