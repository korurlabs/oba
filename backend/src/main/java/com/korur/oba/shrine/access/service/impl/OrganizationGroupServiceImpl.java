package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.mapper.TextMapper;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.constant.service.OrganizationGroupServiceLogConstant;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupCreateRequest;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupMemberAddRequest;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupUpdateRequest;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupMemberResponse;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.helper.AccessHelper;
import com.korur.oba.shrine.access.mapper.OrganizationGroupMapper;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupMemberRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupRepository;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.service.AppAccessRevocationService;
import com.korur.oba.shrine.access.service.OrganizationGroupQueryService;
import com.korur.oba.shrine.access.service.OrganizationGroupService;
import com.korur.oba.shrine.access.validator.OrganizationGroupValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

/**
 * Implements the write operations for organization groups.
 *
 * A group is read by its id and the organization of the request, so a group of another organization is not
 * found. A name check here gives the caller a clear error; the partial unique index on lower(name) stops a
 * parallel duplicate that passes it. The check uses the trimmed name that the mapper stores, so a name that
 * differs from another only in surrounding spaces is refused too. Removing a group or a member row can
 * take the group's apps from a user, so those writes hand the affected users to
 * {@link AppAccessRevocationService}.
 */
@Service
@RequiredArgsConstructor
public class OrganizationGroupServiceImpl implements OrganizationGroupService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationGroupServiceImpl.class);

    private final OrganizationGroupRepository organizationGroupRepository;
    private final OrganizationGroupMemberRepository organizationGroupMemberRepository;
    private final AppGrantRepository appGrantRepository;
    private final OrganizationGroupMapper organizationGroupMapper;
    private final OrganizationGroupQueryService organizationGroupQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final OrganizationValidator organizationValidator;
    private final OrganizationGroupValidator organizationGroupValidator;
    private final AppAccessRevocationService appAccessRevocationService;
    private final AccessHelper accessHelper;
    private final AuditLogMapper auditLogMapper;
    private final AuditService auditService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public OrganizationGroupResponse createGroup(User actor, UUID organizationId,
                                                 OrganizationGroupCreateRequest request) {
        Organization organization = organizationQueryService.getOrganizationEntityById(organizationId);
        String name = TextMapper.trimText(request.name());
        if (organizationGroupRepository.existsByOrganization_IdAndNameIgnoreCase(organizationId, name)) {
            throw nameTaken(organizationId, name);
        }

        OrganizationGroup group = organizationGroupMapper.toEntity(request);
        group.setOrganization(organization);
        group = organizationGroupRepository.save(group);

        auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                OrganizationGroupServiceLogConstant.AUDIT_ACTION_GROUP_CREATED,
                OrganizationGroupServiceLogConstant.AUDIT_ENTITY_TYPE, group.getId(),
                null, accessHelper.describeGroup(group)));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OrganizationGroupServiceLogConstant.GROUP_CREATED,
                organizationId, group.getId());

        return organizationGroupMapper.toResponse(group, 0L, List.of());
    }

    @Override
    @Transactional
    public OrganizationGroupResponse updateGroup(User actor, UUID organizationId, UUID groupId,
                                                 OrganizationGroupUpdateRequest request) {
        OrganizationGroup group = organizationGroupQueryService.getGroupEntity(organizationId, groupId);
        organizationGroupValidator.ensureCustomGroup(organizationId, group);
        String name = TextMapper.trimText(request.name());
        if (organizationGroupRepository.existsByOrganization_IdAndNameIgnoreCaseAndIdNot(
                organizationId, name, groupId)) {
            throw nameTaken(organizationId, name);
        }

        Map<String, Object> oldValues = accessHelper.describeGroup(group);
        organizationGroupMapper.updateEntity(group, request);
        group = organizationGroupRepository.save(group);

        auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                OrganizationGroupServiceLogConstant.AUDIT_ACTION_GROUP_UPDATED,
                OrganizationGroupServiceLogConstant.AUDIT_ENTITY_TYPE, group.getId(),
                oldValues, accessHelper.describeGroup(group)));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OrganizationGroupServiceLogConstant.GROUP_UPDATED,
                organizationId, group.getId());

        return organizationGroupMapper.toResponse(group,
                organizationGroupMemberRepository.countByGroup_Id(groupId),
                accessHelper.appsOf(appGrantRepository.findByGroup_Id(groupId)));
    }

    @Override
    @Transactional
    public void deleteGroup(User actor, UUID organizationId, UUID groupId) {
        OrganizationGroup group = organizationGroupQueryService.getGroupEntity(organizationId, groupId);
        organizationGroupValidator.ensureCustomGroup(organizationId, group);

        List<OrganizationGroupMember> memberRows = organizationGroupMemberRepository.findByGroup_Id(groupId);
        List<AppGrant> grants = appGrantRepository.findByGroup_Id(groupId);
        List<App> apps = accessHelper.appsOf(grants);

        Map<App, Set<UUID>> holdersBefore = appAccessQueryService.findAppHolders(
                organizationId, apps, accessHelper.userIdsOf(memberRows));

        Map<String, Object> oldValues = accessHelper.describeGroup(group);
        oldValues.put(OrganizationGroupServiceLogConstant.AUDIT_FIELD_MEMBER_COUNT, memberRows.size());
        oldValues.put(OrganizationGroupServiceLogConstant.AUDIT_FIELD_APPS,
                apps.stream().map(App::name).toList());

        organizationGroupMemberRepository.deleteAll(memberRows);
        appGrantRepository.deleteAll(grants);
        organizationGroupRepository.delete(group);

        appAccessRevocationService.publishLostAppAccess(organizationId, holdersBefore);

        auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                OrganizationGroupServiceLogConstant.AUDIT_ACTION_GROUP_DELETED,
                OrganizationGroupServiceLogConstant.AUDIT_ENTITY_TYPE, group.getId(),
                oldValues,
                Map.of(OrganizationGroupServiceLogConstant.AUDIT_FIELD_SOFT_DELETED, true)));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OrganizationGroupServiceLogConstant.GROUP_DELETED,
                organizationId, group.getId(), memberRows.size(), grants.size());
    }

    @Override
    @Transactional
    public List<OrganizationGroupMemberResponse> addMembers(User actor, UUID organizationId, UUID groupId,
                                                            OrganizationGroupMemberAddRequest request) {
        OrganizationGroup group = organizationGroupQueryService.getGroupEntity(organizationId, groupId);
        organizationGroupValidator.ensureCustomGroup(organizationId, group);

        // Every user is checked before any row is written, so one stranger refuses the whole request.
        List<User> users = new LinkedHashSet<>(request.userIds())
                .stream()
                .map(userId -> organizationValidator.ensureMemberExists(organizationId, userId).getUser())
                .toList();

        List<OrganizationGroupMember> newRows = new ArrayList<>();
        for (User user : users) {
            if (organizationGroupMemberRepository.existsByGroup_IdAndUser_Id(groupId, user.getId())) {
                continue;
            }
            newRows.add(OrganizationGroupMember.builder()
                    .group(group)
                    .user(user)
                    .build());
        }

        if (!newRows.isEmpty()) {
            organizationGroupMemberRepository.saveAll(newRows);

            auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                    OrganizationGroupServiceLogConstant.AUDIT_ACTION_GROUP_MEMBERS_ADDED,
                    OrganizationGroupServiceLogConstant.AUDIT_ENTITY_TYPE, groupId,
                    null,
                    Map.of(OrganizationGroupServiceLogConstant.AUDIT_FIELD_USER_IDS,
                            List.copyOf(accessHelper.userIdsOf(newRows)))));
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OrganizationGroupServiceLogConstant.GROUP_MEMBERS_ADDED,
                organizationId, groupId, newRows.size());

        return organizationGroupQueryService.listGroupMembers(actor, organizationId, groupId);
    }

    @Override
    @Transactional
    public void removeMember(User actor, UUID organizationId, UUID groupId, UUID userId) {
        OrganizationGroup group = organizationGroupQueryService.getGroupEntity(organizationId, groupId);
        organizationGroupValidator.ensureCustomGroup(organizationId, group);

        OrganizationGroupMember memberRow =
                organizationGroupValidator.ensureGroupMemberExists(organizationId, groupId, userId);

        Map<App, Set<UUID>> holdersBefore = appAccessQueryService.findAppHolders(
                organizationId, accessHelper.appsOf(appGrantRepository.findByGroup_Id(groupId)), Set.of(userId));

        organizationGroupMemberRepository.delete(memberRow);

        appAccessRevocationService.publishLostAppAccess(organizationId, holdersBefore);

        auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                OrganizationGroupServiceLogConstant.AUDIT_ACTION_GROUP_MEMBER_REMOVED,
                OrganizationGroupServiceLogConstant.AUDIT_ENTITY_TYPE, groupId,
                Map.of(OrganizationGroupServiceLogConstant.AUDIT_FIELD_USER_ID, userId),
                Map.of(OrganizationGroupServiceLogConstant.AUDIT_FIELD_SOFT_DELETED, true)));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OrganizationGroupServiceLogConstant.GROUP_MEMBER_REMOVED,
                organizationId, groupId, userId);
    }

    @Override
    @Transactional
    public void createEveryoneGroup(Organization organization) {
        Optional<OrganizationGroup> existing = organizationGroupRepository
                .findFirstByOrganization_IdAndType(organization.getId(), OrganizationGroupType.EVERYONE);
        if (existing.isPresent()) {
            return;
        }

        OrganizationGroup group = organizationGroupRepository.save(OrganizationGroup.builder()
                .organization(organization)
                .name(AccessConstant.EVERYONE_GROUP_NAME)
                .type(OrganizationGroupType.EVERYONE)
                .build());

        // A new organization opens every app to its members, as the organizations that existed before groups
        // do; an admin who wants less revokes a grant. No user makes these grants, so grantedBy stays null.
        List<AppGrant> grants = Arrays.stream(App.values())
                .<AppGrant>map((App app) -> AppGrant.builder()
                        .organization(organization)
                        .app(app)
                        .group(group)
                        .build())
                .toList();
        appGrantRepository.saveAll(grants);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OrganizationGroupServiceLogConstant.EVERYONE_GROUP_CREATED,
                organization.getId(), group.getId(), grants.size());
    }

    /**
     * Logs and builds the refusal of a group name that another group of the organization already has.
     */
    private ConflictException nameTaken(UUID organizationId, String name) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AccessExceptionLogConstant.GROUP_NAME_TAKEN,
                organizationId, name);
        return new ConflictException(
                ExceptionDomainAccessError.STA_GROUP_NAME_TAKEN,
                Map.of(
                        OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                        AccessConstant.FIELD_GROUP_NAME, name));
    }
}
