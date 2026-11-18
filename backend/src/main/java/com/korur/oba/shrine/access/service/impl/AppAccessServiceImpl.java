package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.service.AppAccessServiceLogConstant;
import com.korur.oba.shrine.access.dto.request.AppGrantCreateRequest;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.helper.AccessHelper;
import com.korur.oba.shrine.access.mapper.AppAccessMapper;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupMemberRepository;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.service.AppAccessRevocationService;
import com.korur.oba.shrine.access.service.AppAccessService;
import com.korur.oba.shrine.access.service.OrganizationGroupQueryService;
import com.korur.oba.shrine.access.validator.AppGrantValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

/**
 * Implements the write operations for app grants.
 *
 * A grant names exactly one target. A target user must be a member of the organization, and a target group
 * must be a group of the organization, so a grant never reaches across organizations. The partial unique
 * indexes on the grants table stop a parallel duplicate that passes the existence check here. Revoking a
 * grant can take its app from the users it reached, so a revoke hands them to
 * {@link AppAccessRevocationService}.
 */
@Service
@RequiredArgsConstructor
public class AppAccessServiceImpl implements AppAccessService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AppAccessServiceImpl.class);

    private final AppGrantRepository appGrantRepository;
    private final OrganizationGroupMemberRepository organizationGroupMemberRepository;
    private final AppAccessMapper appAccessMapper;
    private final AppAccessQueryService appAccessQueryService;
    private final OrganizationGroupQueryService organizationGroupQueryService;
    private final OrganizationValidator organizationValidator;
    private final AppGrantValidator appGrantValidator;
    private final AppAccessRevocationService appAccessRevocationService;
    private final AccessHelper accessHelper;
    private final AuditLogMapper auditLogMapper;
    private final AuditService auditService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public AppGrantResponse grant(User actor, UUID organizationId, AppGrantCreateRequest request) {
        appGrantValidator.ensureOneTarget(organizationId, request);

        AppGrant grant = appAccessMapper.toEntity(request);
        Optional<AppGrant> existing;
        if (request.userId() != null) {
            OrganizationMember member = organizationValidator.ensureMemberExists(organizationId, request.userId());
            grant.setOrganization(member.getOrganization());
            grant.setUser(member.getUser());
            existing = appGrantRepository.findByOrganization_IdAndAppAndUser_Id(
                    organizationId, request.app(), request.userId());
        } else {
            OrganizationGroup group = organizationGroupQueryService.getGroupEntity(organizationId, request.groupId());
            grant.setOrganization(group.getOrganization());
            grant.setGroup(group);
            existing = appGrantRepository.findByGroup_IdAndApp(group.getId(), request.app());
        }

        if (existing.isPresent()) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AppAccessServiceLogConstant.APP_GRANT_ALREADY_EXISTS,
                    organizationId, existing.get().getId(), request.app());
            return appAccessMapper.toResponse(existing.get());
        }

        grant.setGrantedBy(actor);
        grant = appGrantRepository.save(grant);

        auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                AppAccessServiceLogConstant.AUDIT_ACTION_APP_GRANTED,
                AppAccessServiceLogConstant.AUDIT_ENTITY_TYPE, grant.getId(),
                null, accessHelper.describeGrant(grant)));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AppAccessServiceLogConstant.APP_GRANTED,
                organizationId, grant.getId(), grant.getApp());

        return appAccessMapper.toResponse(grant);
    }

    @Override
    @Transactional
    public void revoke(User actor, UUID organizationId, UUID grantId) {
        AppGrant grant = appGrantValidator.ensureGrantExists(organizationId, grantId);

        Map<App, Set<UUID>> holdersBefore = appAccessQueryService.findAppHolders(
                organizationId, Set.of(grant.getApp()), appAccessQueryService.findTargetUserIds(grant));

        appGrantRepository.delete(grant);

        appAccessRevocationService.publishLostAppAccess(organizationId, holdersBefore);

        auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                AppAccessServiceLogConstant.AUDIT_ACTION_APP_REVOKED,
                AppAccessServiceLogConstant.AUDIT_ENTITY_TYPE, grant.getId(),
                accessHelper.describeGrant(grant),
                Map.of(AppAccessServiceLogConstant.AUDIT_FIELD_SOFT_DELETED, true)));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AppAccessServiceLogConstant.APP_GRANT_REVOKED,
                organizationId, grant.getId(), grant.getApp());
    }

    @Override
    @Transactional
    public void removeMemberAccess(User actor, UUID organizationId, UUID userId) {
        List<OrganizationGroupMember> groupRows =
                organizationGroupMemberRepository.findByGroup_Organization_IdAndUser_Id(organizationId, userId);
        List<AppGrant> grants = appGrantRepository.findByOrganization_IdAndUser_Id(organizationId, userId);

        if (!groupRows.isEmpty() || !grants.isEmpty()) {
            organizationGroupMemberRepository.deleteAll(groupRows);
            appGrantRepository.deleteAll(grants);

            auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                    AppAccessServiceLogConstant.AUDIT_ACTION_MEMBER_ACCESS_REMOVED,
                    AppAccessServiceLogConstant.AUDIT_ENTITY_TYPE, userId,
                    Map.of(
                            AppAccessServiceLogConstant.AUDIT_FIELD_GROUP_MEMBER_IDS,
                            groupRows.stream().map(OrganizationGroupMember::getId).toList(),
                            AppAccessServiceLogConstant.AUDIT_FIELD_GRANT_IDS,
                            grants.stream().map(AppGrant::getId).toList()),
                    Map.of(AppAccessServiceLogConstant.AUDIT_FIELD_SOFT_DELETED, true)));
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AppAccessServiceLogConstant.MEMBER_ACCESS_REMOVED,
                organizationId, userId, groupRows.size(), grants.size());
    }
}
