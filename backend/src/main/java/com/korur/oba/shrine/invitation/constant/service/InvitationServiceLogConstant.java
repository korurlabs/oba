package com.korur.oba.shrine.invitation.constant.service;

/**
 * Log message constants for invitation domain services.
 */
public final class InvitationServiceLogConstant {

    private InvitationServiceLogConstant() {
    }

    public static final String INVITATION_CREATING =
            "Creating invitation for email: {} to organization: {}";
    public static final String INVITATION_CREATED =
            "Invitation created with PENDING status for email: {}";
    public static final String INVITATION_REVOKING =
            "Revoking invitation: {}";
    public static final String INVITATION_REVOKED =
            "Invitation revoked: {}";
    public static final String ORGANIZATION_INVITATIONS_REVOKED =
            "Revoked {} pending invitations of deleted organization: {}";

    public static final String INVITATION_DECISION_PROCESSING =
            "Processing invitation decision for invitation: {} with decision: {}";
    public static final String INVITATION_DECISION_ACCEPTED =
            "Invitation accepted — member created for organization: {}";
    public static final String INVITATION_DECISION_DECLINED =
            "Invitation declined for invitation: {}";
    public static final String INVITATION_EXPIRED_ON_ACCESS =
            "Invitation expired on access — marked as EXPIRED for invitation: {}";
}
