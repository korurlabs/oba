package com.korur.oba.dojo.audit.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 4. Internal
import com.korur.oba.dojo.audit.constant.service.AuditServiceLogConstant;
import com.korur.oba.dojo.audit.dto.request.AuditLogRequest;
import com.korur.oba.dojo.audit.entity.AuditLog;
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.repository.AuditLogRepository;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implements shared audit logging for cross-domain lifecycle actions.
 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AuditServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final LoggingService loggingService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditLogRequest request) {
        AuditLog auditLog = auditLogMapper.toEntity(request);

        auditLogRepository.save(auditLog);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                AuditServiceLogConstant.AUDIT_LOG_CREATED, request.action(), request.entityType(), request.entityId());
    }
}
