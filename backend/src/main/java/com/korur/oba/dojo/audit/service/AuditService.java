package com.korur.oba.dojo.audit.service;

// 4. Internal
import com.korur.oba.dojo.audit.dto.request.AuditLogRequest;

/**
 * Shared audit service for logging lifecycle actions across domains.
 * Used for tracking user disable/delete, member removal, organization deletion, etc.
 */
public interface AuditService {

    /**
     * Logs an audit event for a lifecycle action.
     *
     * @param request the audit log request containing all event details
     */
    void log(AuditLogRequest request);
}
