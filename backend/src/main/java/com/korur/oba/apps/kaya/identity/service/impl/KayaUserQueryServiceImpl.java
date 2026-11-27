package com.korur.oba.apps.kaya.identity.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.apps.kaya.identity.constant.IdentityConstant;
import com.korur.oba.apps.kaya.identity.constant.service.UserServiceLogConstant;
import com.korur.oba.apps.kaya.identity.helper.UserDisplayNameHelper;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KayaUserQueryServiceImpl implements KayaUserQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(KayaUserQueryServiceImpl.class);

    private final UserQueryService userQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    @Override
    public User getEntityByKeycloakId(String keycloakId) {
        return userQueryService.findUserByKeycloakId(keycloakId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, UserServiceLogConstant.USER_NOT_FOUND, keycloakId);
                    return new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND,
                            Map.of(IdentityConstant.FIELD_KEYCLOAK_ID, keycloakId));
                });
    }

    @Override
    public Optional<User> findEntityByKeycloakId(String keycloakId) {
        return userQueryService.findUserByKeycloakId(keycloakId);
    }

    @Override
    public User getEntityById(UUID userId) {
        return userQueryService.getUserEntityById(userId);
    }

    @Override
    public User requireMemberUser(UUID organizationId, UUID userId) {
        ensureMember(organizationId, userId);
        return userQueryService.getUserEntityById(userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public User getMemberEntityForUpdate(UUID organizationId, UUID userId) {
        ensureMember(organizationId, userId);
        return userQueryService.getUserEntityForUpdate(userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockUserForKeyShare(UUID userId) {
        userQueryService.lockUserForKeyShare(userId);
    }

    @Override
    public Map<UUID, String> getDisplayNames(UUID organizationId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userQueryService.getUserEntitiesByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(User::getId, UserDisplayNameHelper::displayNameOf));
    }

    private void ensureMember(UUID organizationId, UUID userId) {
        // A user of another organization answers the same 404 as an unknown id, so ids cannot be
        // probed across tenants.
        if (organizationMemberQueryService.findMember(organizationId, userId).isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, UserServiceLogConstant.MEMBER_USER_NOT_FOUND, userId, organizationId);
            throw new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                    Map.of(IdentityConstant.FIELD_USER_ID, userId.toString()));
        }
    }
}
