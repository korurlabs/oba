package com.korur.oba.apps.kaizen.organizationsetting.constant;

/**
 * Field names and request paths of the Kaizen organization settings domain.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationSettingConstant {

    private OrganizationSettingConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Path of the security board of an organization in the Kaizen API.
     */
    public static final String SECURITY_BOARD_PATH = "/kaizen/organizations/{organizationId}/security-board";

    /**
     * Platform path of the security board that Kaizen web releases before the move
     * to the Kaizen API still call. It answers with the same service until no
     * deployed client calls it.
     */
    public static final String LEGACY_SECURITY_BOARD_PATH = "/organizations/{organizationId}/security-board";
}
