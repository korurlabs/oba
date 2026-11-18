package com.korur.oba.apps.kalkan.permission.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.core.exception.enums.ExceptionDomainKalkanError;
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.permission.constant.KalkanPagePermissionConstant;
import com.korur.oba.apps.kalkan.permission.constant.service.KalkanPagePermissionServiceLogConstant;
import com.korur.oba.apps.kalkan.permission.dto.projection.KalkanRootLevelProjection;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.repository.KalkanPagePermissionRepository;
import com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * The {@link KalkanAccessQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KalkanAccessQueryServiceImpl implements KalkanAccessQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(KalkanAccessQueryServiceImpl.class);

    private final KalkanPagePermissionRepository kalkanPagePermissionRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    @Override
    public KalkanLevel resolveLevel(UUID rootId, User user, OrganizationMemberRole role) {
        if (role == null) {
            return null;
        }
        if (role == OrganizationMemberRole.OWNER) {
            return KalkanLevel.MANAGE;
        }
        KalkanLevel granted = kalkanPagePermissionRepository.findLevel(rootId, user.getId()).orElse(null);
        return capForRole(granted, role);
    }

    @Override
    public KalkanLevel verifyLevel(UUID organizationId, KalkanPage page, User user, KalkanLevel minimum) {
        return verifyLevel(organizationId, page.getId(), page.getAccessRootId(), user, minimum);
    }

    @Override
    public KalkanLevel verifyLevel(UUID organizationId, UUID pageId, UUID rootId, User user, KalkanLevel minimum) {
        KalkanLevel level = resolveLevel(rootId, user, findRole(organizationId, user));
        if (level == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPagePermissionServiceLogConstant.PAGE_HIDDEN,
                    user.getId(), rootId, pageId, organizationId);
            throw new NotFoundException(ExceptionDomainKalkanError.NOT_PAGE_FOUND,
                    Map.of(KalkanPageConstant.FIELD_PAGE_ID, pageId.toString()));
        }
        if (!level.isAtLeast(minimum)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPagePermissionServiceLogConstant.LEVEL_TOO_LOW,
                    user.getId(), level, rootId, pageId, organizationId, minimum);
            throw new ForbiddenException(ExceptionDomainKalkanError.SEC_LEVEL_TOO_LOW,
                    Map.of(KalkanPagePermissionConstant.FIELD_LEVEL, minimum.name()));
        }
        return level;
    }

    @Override
    public void requireRootCreate(UUID organizationId, User user) {
        OrganizationMemberRole role = findRole(organizationId, user);
        if (role == null || !role.isAtLeast(OrganizationMemberRole.ADMIN)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPagePermissionServiceLogConstant.ROOT_CREATE_REFUSED,
                    user.getId(), role, organizationId);
            throw new ForbiddenException(ExceptionDomainKalkanError.SEC_LEVEL_TOO_LOW,
                    Map.of(KalkanPagePermissionConstant.FIELD_ROLE, OrganizationMemberRole.ADMIN.name()));
        }
    }

    @Override
    public Map<UUID, KalkanLevel> resolveLevels(UUID organizationId, Collection<UUID> rootIds, User user) {
        Map<UUID, KalkanLevel> levels = new HashMap<>();
        OrganizationMemberRole role = findRole(organizationId, user);
        if (role == null || rootIds.isEmpty()) {
            return levels;
        }
        if (role == OrganizationMemberRole.OWNER) {
            for (UUID rootId : rootIds) {
                levels.put(rootId, KalkanLevel.MANAGE);
            }
            return levels;
        }
        for (KalkanRootLevelProjection row : kalkanPagePermissionRepository.findLevels(rootIds, user.getId())) {
            levels.put(row.getRootId(), capForRole(row.getLevel(), role));
        }
        return levels;
    }

    // The role comes from the database, never from the request; no membership means no level.
    private OrganizationMemberRole findRole(UUID organizationId, User user) {
        return organizationMemberQueryService.findMember(organizationId, user.getId())
                .map(OrganizationMember::getRole)
                .orElse(null);
    }

    // A guest only reads, whatever a grant says, so a later role change cannot leave a guest editing.
    private KalkanLevel capForRole(KalkanLevel granted, OrganizationMemberRole role) {
        if (granted == null) {
            return null;
        }
        if (role == OrganizationMemberRole.GUEST) {
            return KalkanLevel.VIEW;
        }
        return granted;
    }
}
