package com.korur.oba.apps.kaya.identity.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.identity.constant.service.UserServiceLogConstant;
import com.korur.oba.apps.kaya.identity.entity.UserAuditEntry;
import com.korur.oba.apps.kaya.identity.repository.UserAuditEntryRepository;
import com.korur.oba.apps.kaya.identity.service.UserAuditRecorder;

@Service
@RequiredArgsConstructor
public class UserAuditRecorderImpl implements UserAuditRecorder {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(UserAuditRecorderImpl.class);

    private final UserAuditEntryRepository userAuditEntryRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UserAuditEntry entry) {
        UserAuditEntry saved = userAuditEntryRepository.save(entry);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, UserServiceLogConstant.AUDIT_RECORDED,
                saved.getAction(), saved.getUser().getId(), saved.getOrganizationId(),
                saved.getActorKind(), saved.getActorId());
    }
}
