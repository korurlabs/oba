package com.korur.oba.connect.notification.enums;

// 2. Third-party
import lombok.Getter;

/**
 * Email template types for Sensey notifications.
 */
@Getter
public enum EmailTemplateType {

    // Onboarding
    WELCOME_EMAIL("welcome-email"),

    // Organization Invitations (App-driven, not Keycloak)
    ORGANIZATION_INVITATION_EXISTING_USER("organization-invitation-existing-user"),
    ORGANIZATION_INVITATION_NEW_USER("organization-invitation-new-user"),
    ORGANIZATION_INVITATION_ACCEPTED("organization-invitation-accepted"),
    ORGANIZATION_MEMBER_REMOVED("organization-member-removed"),
    ORGANIZATION_ROLE_CHANGED("organization-role-changed"),

    // Passchat
    PASSCHAT_SECRET_SHARED("passchat-secret-shared"),
    PASSCHAT_SECRET_EXPIRED("passchat-secret-expired"),
    PASSCHAT_STORAGE_ACK_REQUIRED("passchat-storage-ack-required"),

    // Security & Alerts (app-side only)
    UNUSUAL_ACTIVITY_DETECTED("unusual-activity-detected"),
    DATA_EXPORT_READY("data-export-ready"),
    ACCOUNT_STATUS_CHANGED("account-status-changed"),

    // System
    SYSTEM_NOTIFICATION("system-notification");

    private final String templateName;

    EmailTemplateType(String templateName) {
        this.templateName = templateName;
    }
}
