package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.service.AppAccessServiceLogConstant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.service.AppAccessRevocationService;

/**
 * Implements the publishing of lost app access.
 *
 * The access check after the write goes through {@link AppAccessQueryService}, the same rule that let the
 * user in, so an event names exactly the apps the user can no longer open.
 */
@Service
@RequiredArgsConstructor
public class AppAccessRevocationServiceImpl implements AppAccessRevocationService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AppAccessRevocationServiceImpl.class);

    private final AppAccessQueryService appAccessQueryService;
    private final AppGrantRepository appGrantRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public void publishLostAppAccess(UUID organizationId, Map<App, Set<UUID>> holdersBefore) {
        if (holdersBefore.isEmpty()) {
            return;
        }
        // The grant check is a native query, which reads only rows that are already in the database.
        // The flush writes every pending change of the transaction, a new member role included.
        appGrantRepository.flush();
        holdersBefore.forEach((App app, Set<UUID> userIds) -> {
            for (UUID userId : userIds) {
                if (appAccessQueryService.hasAppAccess(organizationId, userId, app)) {
                    continue;
                }
                applicationEventPublisher.publishEvent(new AppAccessRevokedEvent(userId, organizationId, app));
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AppAccessServiceLogConstant.APP_ACCESS_REVOKED,
                        organizationId, userId, app);
            }
        });
    }
}
