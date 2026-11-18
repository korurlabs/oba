package com.korur.oba.apps.kalkan.permission.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kalkan.core.exception.enums.ExceptionDomainKalkanError;
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.service.KalkanPageQueryService;
import com.korur.oba.apps.kalkan.permission.constant.KalkanPagePermissionConstant;
import com.korur.oba.apps.kalkan.permission.constant.service.KalkanPagePermissionServiceLogConstant;
import com.korur.oba.apps.kalkan.permission.dto.response.KalkanPagePermissionResponse;
import com.korur.oba.apps.kalkan.permission.entity.KalkanPagePermission;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.mapper.KalkanPagePermissionMapper;
import com.korur.oba.apps.kalkan.permission.repository.KalkanPagePermissionRepository;
import com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService;
import com.korur.oba.apps.kalkan.permission.service.KalkanPermissionQueryService;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * The {@link KalkanPermissionQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KalkanPermissionQueryServiceImpl implements KalkanPermissionQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(KalkanPermissionQueryServiceImpl.class);

    private final KalkanPagePermissionRepository kalkanPagePermissionRepository;
    private final KalkanPageQueryService kalkanPageQueryService;
    private final KalkanAccessQueryService kalkanAccessQueryService;
    private final UserQueryService userQueryService;
    private final KalkanPagePermissionMapper kalkanPagePermissionMapper;
    private final LoggingService loggingService;

    @Override
    public List<KalkanPagePermissionResponse> getPermissions(User user, UUID organizationId, UUID pageId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, KalkanPagePermissionServiceLogConstant.LISTING_PERMISSIONS,
                pageId, organizationId);
        return getPermissionResponses(getManagedRoot(user, organizationId, pageId));
    }

    @Override
    public KalkanPage getManagedRoot(User user, UUID organizationId, UUID pageId) {
        KalkanPage page = kalkanPageQueryService.getEntity(organizationId, pageId);
        // The level check comes first, so a hidden page answers 404 before its place in the tree is revealed.
        kalkanAccessQueryService.verifyLevel(organizationId, page, user, KalkanLevel.MANAGE);
        if (page.getParent() != null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPagePermissionServiceLogConstant.PERMISSION_ON_CHILD,
                    user.getId(), page.getId(), organizationId);
            throw new ValidationException(ExceptionDomainKalkanError.VAL_PERMISSION_ON_CHILD,
                    Map.of(KalkanPageConstant.FIELD_PAGE_ID, page.getId().toString()));
        }
        return page;
    }

    @Override
    public List<KalkanPagePermissionResponse> getPermissionResponses(KalkanPage root) {
        List<KalkanPagePermission> permissions = kalkanPagePermissionRepository
                .findByPage_IdOrderByCreatedAtAsc(root.getId());
        if (permissions.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = permissions.stream().map(KalkanPagePermission::getUserId).distinct().toList();
        Map<UUID, User> users = userQueryService.getUserEntitiesByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return permissions.stream()
                .map(permission -> kalkanPagePermissionMapper.toResponse(permission,
                        displayNameOf(users.get(permission.getUserId()))))
                .toList();
    }

    // The member's full name, else the email, as the other apps show members.
    private static String displayNameOf(User user) {
        if (user == null) {
            return null;
        }
        UserProfile profile = user.getProfile();
        String firstName = profile == null || profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String lastName = profile == null || profile.getLastName() == null ? "" : profile.getLastName().trim();
        String name = (firstName + KalkanPagePermissionConstant.NAME_SEPARATOR + lastName).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
