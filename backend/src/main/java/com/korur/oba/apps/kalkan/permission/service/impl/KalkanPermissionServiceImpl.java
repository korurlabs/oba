package com.korur.oba.apps.kalkan.permission.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.core.exception.enums.ExceptionDomainKalkanError;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.permission.constant.KalkanPagePermissionConstant;
import com.korur.oba.apps.kalkan.permission.constant.service.KalkanPagePermissionServiceLogConstant;
import com.korur.oba.apps.kalkan.permission.dto.request.KalkanPagePermissionRequest;
import com.korur.oba.apps.kalkan.permission.dto.response.KalkanPagePermissionResponse;
import com.korur.oba.apps.kalkan.permission.entity.KalkanPagePermission;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.repository.KalkanPagePermissionRepository;
import com.korur.oba.apps.kalkan.permission.service.KalkanPermissionQueryService;
import com.korur.oba.apps.kalkan.permission.service.KalkanPermissionService;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * The {@link KalkanPermissionService} implementation.
 */
@Service
@RequiredArgsConstructor
public class KalkanPermissionServiceImpl implements KalkanPermissionService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(KalkanPermissionServiceImpl.class);

    private final KalkanPagePermissionRepository kalkanPagePermissionRepository;
    private final KalkanPermissionQueryService kalkanPermissionQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;
    private final Clock clock;

    @Override
    @Transactional
    public List<KalkanPagePermissionResponse> replacePermissions(User user, UUID organizationId, UUID pageId,
                                                                 List<KalkanPagePermissionRequest> requests) {
        KalkanPage root = kalkanPermissionQueryService.getManagedRoot(user, organizationId, pageId);
        Map<UUID, KalkanLevel> requested = validateRequests(root, organizationId, requests);

        // A subject keeps its row only at the same level; any change is a revocation and a new grant.
        List<UUID> revokedIds = new ArrayList<>();
        Map<UUID, KalkanLevel> kept = new HashMap<>();
        for (KalkanPagePermission live : kalkanPagePermissionRepository.findByPage_IdOrderByCreatedAtAsc(root.getId())) {
            if (live.getLevel() == requested.get(live.getUserId())) {
                kept.put(live.getUserId(), live.getLevel());
            } else {
                revokedIds.add(live.getId());
            }
        }
        if (!revokedIds.isEmpty()) {
            kalkanPagePermissionRepository.softDeleteByIds(revokedIds, user.getId(), Instant.now(clock));
        }

        List<KalkanPagePermission> granted = new ArrayList<>();
        for (Map.Entry<UUID, KalkanLevel> entry : requested.entrySet()) {
            if (!kept.containsKey(entry.getKey())) {
                granted.add(newGrant(root, entry.getKey(), entry.getValue(), user.getId()));
            }
        }
        kalkanPagePermissionRepository.saveAllAndFlush(granted);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, KalkanPagePermissionServiceLogConstant.PERMISSIONS_REPLACED,
                root.getId(), organizationId, user.getId(), revokedIds.size(), granted.size());
        return kalkanPermissionQueryService.getPermissionResponses(root);
    }

    @Override
    @Transactional
    public void grantCreator(KalkanPage root, UUID userId) {
        kalkanPagePermissionRepository.save(newGrant(root, userId, KalkanLevel.MANAGE, userId));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, KalkanPagePermissionServiceLogConstant.CREATOR_GRANTED,
                root.getId(), userId);
    }

    /**
     * Checks every grant of a replacement list and returns the level per member.
     *
     * Only an ACTIVE member with an ACTIVE account can hold a grant, so a removed or disabled
     * member is not given access that would wake up with a later re-activation.
     */
    private Map<UUID, KalkanLevel> validateRequests(KalkanPage root, UUID organizationId,
                                                    List<KalkanPagePermissionRequest> requests) {
        Map<UUID, KalkanLevel> requested = new HashMap<>();
        for (KalkanPagePermissionRequest request : requests) {
            if (request.groupId() != null) {
                throw invalidSubject(root, organizationId, request.groupId(),
                        KalkanPagePermissionConstant.FIELD_GROUP_ID, KalkanPagePermissionConstant.REASON_GROUPS_NOT_SUPPORTED);
            }
            UUID userId = request.userId();
            if (userId == null) {
                throw invalidSubject(root, organizationId, null,
                        KalkanPagePermissionConstant.FIELD_USER_ID, KalkanPagePermissionConstant.REASON_NO_SUBJECT);
            }
            if (requested.containsKey(userId)) {
                throw invalidSubject(root, organizationId, userId,
                        KalkanPagePermissionConstant.FIELD_USER_ID, KalkanPagePermissionConstant.REASON_DUPLICATE_SUBJECT);
            }
            OrganizationMember member = organizationMemberQueryService.findMember(organizationId, userId)
                    .filter(found -> found.getStatus() == OrganizationMemberStatus.ACTIVE)
                    .filter(found -> found.getUser().getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> invalidSubject(root, organizationId, userId,
                            KalkanPagePermissionConstant.FIELD_USER_ID, KalkanPagePermissionConstant.REASON_NOT_ACTIVE_MEMBER));
            if (member.getRole() == OrganizationMemberRole.GUEST && request.level() != KalkanLevel.VIEW) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPagePermissionServiceLogConstant.GUEST_ABOVE_VIEW,
                        request.level(), userId, root.getId(), organizationId);
                throw new ValidationException(ExceptionDomainKalkanError.VAL_GUEST_ABOVE_VIEW,
                        Map.of(KalkanPagePermissionConstant.FIELD_USER_ID, userId.toString(),
                                KalkanPagePermissionConstant.FIELD_LEVEL, request.level().name()));
            }
            requested.put(userId, request.level());
        }
        return requested;
    }

    private ValidationException invalidSubject(KalkanPage root, UUID organizationId, UUID subjectId, String field,
                                               String reason) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPagePermissionServiceLogConstant.INVALID_SUBJECT,
                root.getId(), organizationId, subjectId, reason);
        Map<String, Object> errorValues = new HashMap<>();
        errorValues.put(KalkanPagePermissionConstant.FIELD_REASON, reason);
        if (subjectId != null) {
            errorValues.put(field, subjectId.toString());
        }
        return new ValidationException(ExceptionDomainKalkanError.VAL_SUBJECT_NOT_ACTIVE, errorValues);
    }

    private KalkanPagePermission newGrant(KalkanPage root, UUID userId, KalkanLevel level, UUID grantedById) {
        return KalkanPagePermission.builder()
                .page(root)
                .userId(userId)
                .level(level)
                .createdById(grantedById)
                .updatedById(grantedById)
                .build();
    }
}
