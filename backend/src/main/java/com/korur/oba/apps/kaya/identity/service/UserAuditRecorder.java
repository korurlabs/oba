package com.korur.oba.apps.kaya.identity.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.identity.entity.UserAuditEntry;

/**
 * Writes the audit log of users. Every audited action on a user records exactly one entry, in the
 * transaction of the action, so the action and its entry commit or roll back together.
 */
public interface UserAuditRecorder {

    /**
     * Inserts one audit entry. It must be called inside the caller's transaction; without one it
     * throws IllegalTransactionStateException, so an action can never be audited on its own.
     *
     * Build the entry with UserAuditEntry.builder(): organizationId, user, actorKind and action
     * are required; actorId is the acting user for USER entries and null otherwise; reason is
     * optional; details defaults to an empty object.
     *
     * @param entry the entry to insert; it must not have been saved before
     */
    void record(UserAuditEntry entry);
}
