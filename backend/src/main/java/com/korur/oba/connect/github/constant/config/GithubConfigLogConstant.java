package com.korur.oba.connect.github.constant.config;

/**
 * Log messages of the GitHub integration's configuration.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class GithubConfigLogConstant {

    private GithubConfigLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String INTEGRATION_ENABLED = "GitHub integration enabled for organization {}";
    public static final String INTEGRATION_DISABLED =
            "GitHub integration disabled: one of the oba.integrations.github keys is blank";
}
