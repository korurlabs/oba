package com.korur.oba.apps.kaya.guardrail.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits and error value keys of the guardrail domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class GuardrailConstant {

    private GuardrailConstant() {
    }

    // --- Entity fields (specifications and sorting) ---
    public static final String FIELD_ID = "id";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_VERSION_ID = "versionId";
    public static final String FIELD_NAME = "name";

    // --- Error value keys ---
    public static final String FIELD_GUARDRAIL_ID = "guardrailId";

    // --- File limits, which the parse holds a guardrail file to ---
    // The file name without .md: lower case letters, digits and hyphens, starting with a letter or digit.
    public static final String NAME_PATTERN = "^[a-z0-9][a-z0-9-]{0,59}$";
    public static final int NAME_MAX_LENGTH = 60;
    public static final int BODY_MAX_CHARACTERS = 4000;
    public static final int DENY_MAX = 20;
    public static final int DENY_RULE_MAX_LENGTH = 500;
    // A guardrail's path below .agents/rules/guardrails/ is its name with this suffix.
    public static final String FILE_SUFFIX = ".md";

    // --- Request limits ---
    public static final int VERSION_IDS_MAX = 20;
    public static final String VERSION_IDS_SIZE_MESSAGE = "must name 1 to 20 version ids";
}
