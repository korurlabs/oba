package com.korur.oba.apps.kazan.organizationsetting.constant;

/**
 * Field names and request paths of the Kazan organization settings domain.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationSettingConstant {

    private OrganizationSettingConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Path of the security board of an organization in the Kazan API.
     */
    public static final String SECURITY_BOARD_PATH = "/kazan/organizations/{organizationId}/security-board";

    /**
     * Platform path of the security board that Kazan web releases before the move
     * to the Kazan API still call. It answers with the same service until no
     * deployed client calls it.
     */
    public static final String LEGACY_SECURITY_BOARD_PATH = "/organizations/{organizationId}/security-board";
}
