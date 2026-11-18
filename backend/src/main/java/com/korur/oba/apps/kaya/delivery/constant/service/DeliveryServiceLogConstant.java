package com.korur.oba.apps.kaya.delivery.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the delivery services. Content is never logged, only ids, numbers and hashes.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DeliveryServiceLogConstant {

    private DeliveryServiceLogConstant() {
    }

    // --- Repository of a remote URL ---
    public static final String REMOTE_URL_INVALID = "Delivery refused a remote URL it cannot normalise, organization {}";
    public static final String REPOSITORY_NOT_FOUND = "No repository {} in organization {} for delivery";
    public static final String REPOSITORY_ARCHIVED = "Delivery refused archived repository {} of organization {}";

    // --- Releases ---
    public static final String CURRENT_RESOLVED = "Current release of repository {} of organization {}: {}";
    public static final String RELEASE_RESOLVED = "Serving release {} number {} of repository {} of organization {}";
    public static final String RELEASE_REMOTE_CHANGED = "Release {} of repository {} no longer matches its signed manifest {}; "
            + "the repository's remote URL changed after it was released";

    // --- Blobs ---
    public static final String BLOB_NOT_DELIVERED = "Blob {} is in no release of organization {}";
    public static final String BLOB_RESOLVED = "Serving blob {} of organization {}";

    // --- Proposals ---
    public static final String PROPOSAL_DUPLICATE_PATHS = "Proposal from machine {} names {} paths more than once";
    public static final String PROPOSAL_NEW_PATH_NO_OWNER = "Proposal on repository {} of organization {} names a new path that no single pack owns: {} candidates";
    public static final String PROPOSAL_SPANS_OWNERS = "Proposal from machine {} on release {} spans {} packs";
    public static final String PROPOSAL_BREAKS_RULES = "Proposal from machine {} as version {} breaks {} composition rules; refused";
    public static final String PROPOSAL_RESOLVED = "Proposal from machine {} on release {} of repository {} goes to pack {}";
}
