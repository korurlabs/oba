package com.korur.oba.shrine.invitation.constant;

/**
 * Shared constants for the invitation domain.
 */
public final class InvitationConstant {

    private InvitationConstant() {
    }

    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_INVITATION_ID = "invitationId";
    public static final String FIELD_DECISION = "decision";

    public static final int INVITATION_EXPIRY_DAYS = 7;
}
