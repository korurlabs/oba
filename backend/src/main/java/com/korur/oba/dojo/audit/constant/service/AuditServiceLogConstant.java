package com.korur.oba.dojo.audit.constant.service;

/**
 * Log messages used by the shared audit service.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AuditServiceLogConstant {

    private AuditServiceLogConstant() {
    }

    public static final String AUDIT_LOG_CREATED =
            "Audit log created for action: {} on entity type: {} with ID: {}";
}
