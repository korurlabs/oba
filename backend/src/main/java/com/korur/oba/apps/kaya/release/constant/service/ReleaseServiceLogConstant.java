package com.korur.oba.apps.kaya.release.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the release and pack order services, the not-found and refusal lines included.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ReleaseServiceLogConstant {

    private ReleaseServiceLogConstant() {
    }

    // --- Pack order ---
    public static final String LISTING_PACKS = "Listing the packs repository {} of organization {} composes";
    public static final String REPLACING_PACKS = "User {} replacing the packs of repository {} of organization {} with {}";
    public static final String PACKS_UNCHANGED = "Packs of repository {} of organization {} unchanged; no release";
    public static final String PACK_NOT_FOUND = "Pack {} not found in organization {}";
    public static final String MOUNT_REFUSED = "Mount folder {} of repository {} of organization {} refused: {}";
    public static final String PACKS_REPLACED = "Audit: repository {} of organization {} now composes {} packs";
    public static final String SETTING_PACK_REQUIRED = "User {} setting pack {} of organization {} required {}";
    public static final String PACK_REQUIRED_UNCHANGED = "Pack {} of organization {} is already required {}; no release";
    public static final String PACK_REQUIRED_RELEASED = "Audit: pack {} of organization {} set required {}; {} of {} active repositories released";

    // --- Composition ---
    public static final String COMPOSING = "Composing repository {} of organization {} with {} replaced versions";
    public static final String ITEMS_LOCKED = "Pack {} of organization {} stores {} items its ancestors lock";
    public static final String MOUNT_RULE_UNSCOPED = "Rule {} of pack {} of organization {} is not scoped to {} of its mount folders";
    public static final String PACK_REQUIRED_OVERRIDDEN =
            "Pack {} of organization {} not marked required: descendants override {} of its items";
    public static final String VERSION_CHECKED = "Checked the compositions of version {} of organization {}: {} violations";

    // --- Releases ---
    public static final String RELEASING_VERSION = "Releasing version {} of organization {} to {} repositories";
    public static final String RELEASING_REPOSITORY = "Releasing repository {} of organization {} for version {}";
    public static final String RELEASE_CREATED = "Audit: created release {} of repository {} of organization {} with {} files, key {}";
    public static final String BLOBS_STORED = "Stored {} release blobs for repository {} of organization {}";
    public static final String RELEASE_SKIPPED = "No release for repository {} of organization {}: no composed pack or project has an approved version";
    public static final String RELEASE_INSERT_LOST = "Release {} of repository {} was inserted but cannot be read back";
    public static final String RELEASE_MANIFEST_WRITE_FAILED = "Writing the manifest or pack versions of release {} of repository {} failed";
    public static final String LISTING_RELEASES = "Listing releases of repository {} of organization {} with {}";
    public static final String READING_RELEASE = "Reading release {} of repository {} of organization {}";
    public static final String RELEASE_NOT_FOUND = "Release {} of repository {} not found in organization {}";
    public static final String RELEASE_ID_NOT_FOUND = "Release {} not found in organization {}";

    // --- Effective view ---
    public static final String READING_EFFECTIVE = "Reading the effective items of pack {} at version {} in organization {}, "
            + "inherited {}";
    public static final String VERSION_NOT_OF_PACK = "Version {} is not a version of pack {} in organization {}";
}
