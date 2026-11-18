package com.korur.oba.connect.outbox.enums;

/**
 * Event types that the outbox worker can translate into external system actions.
 *
 * Each event type maps to a specific target system handler.
 */
public enum OutboxEventType {

    // ── Notification flows ────────────────────────────────────────────────────
    INVITATION_EXISTING_USER,          // notification - user added to organization directly
    INVITATION_NEW_USER,               // notification - registration invite for non-existing user
    ORGANIZATION_MEMBER_REMOVED,       // notification - member removed from organization
    ORGANIZATION_ROLE_CHANGED,         // notification - member role changed
    PASSCHAT_SECRET_SHARED,            // notification - secret shared with recipients
    PASSCHAT_SECRET_EXPIRED,           // notification - secret expired
    PASSCHAT_STORAGE_ACK_REQUIRED,     // notification - storage acknowledgement required
    UNUSUAL_ACTIVITY_DETECTED,         // notification - unusual activity on account
    WELCOME_EMAIL,                     // notification - welcome email after onboarding
    ACCOUNT_STATUS_CHANGED,            // notification - account status change
    DATA_EXPORT_READY,                 // notification - data export is ready
    SYSTEM_NOTIFICATION,               // notification - generic system notification

    // ── Azure Blob Storage flows ──────────────────────────────────────────────
    AZURE_BLOB_UPLOAD,                 // azure - file upload to blob storage
    AZURE_BLOB_DELETE,                 // azure - file deletion from blob storage

    // ── Kubernetes flows ──────────────────────────────────────────────────────
    KUBERNETES_COMMAND_EXECUTION,      // kubernetes - command execution in pod
    KUBERNETES_DEPLOYMENT_TRIGGER,     // kubernetes - deployment trigger

    // ── Intelligence flows ────────────────────────────────────────────────────
    INTELLIGENCE_CHAT_REQUEST,         // intelligence - text chat request
    INTELLIGENCE_MULTIMODAL_REQUEST    // intelligence - multimodal (image) chat request
}
