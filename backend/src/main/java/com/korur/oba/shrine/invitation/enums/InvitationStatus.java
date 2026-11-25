package com.korur.oba.shrine.invitation.enums;

/**
 * Invitation lifecycle status.
 */
public enum InvitationStatus {
    /**
     * Invitation has been sent and is waiting for the user to accept or decline.
     */
    PENDING,

    /**
     * Invitation was accepted by the user.
     */
    ACCEPTED,

    /**
     * Invitation has expired (past its expiry date).
     */
    EXPIRED,

    /**
     * Invitation was declined by the user.
     */
    DECLINED,

    /**
     * Invitation has been revoked by an admin, or because its organization was deleted.
     */
    REVOKED
}
