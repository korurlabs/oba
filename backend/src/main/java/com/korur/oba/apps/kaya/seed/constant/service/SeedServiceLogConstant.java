package com.korur.oba.apps.kaya.seed.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the seed. File content is never logged, only paths and hashes.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SeedServiceLogConstant {

    private SeedServiceLogConstant() {
    }

    public static final String SEEDING = "User {} seeds organization {} from {} with {} sets";
    public static final String SEED_REQUEST_REFUSED = "Seed of organization {} refused: {}";
    public static final String SEED_TOO_MANY_FILES = "Seed of organization {} refused: {} files, over the limit of {}";
    public static final String SEED_CONTENT_TOO_LARGE = "Seed of organization {} refused: {} bytes of content, over the limit of {}";
    public static final String SEED_BLOBS_MISSING = "Seed of organization {} names {} blobs that are not uploaded";
    public static final String SEED_FILE_REFUSED = "Seed of organization {} refused {} in {} {}: {}";
    public static final String SEED_FILE_WARNED = "Seed of organization {} imports {} in {} {} over a size limit: {}";
    public static final String SEED_LINK_LEFT_OUT = "Seed of organization {} left out the link {} in {} {}: it leads to {}";
    public static final String SEED_VERSION_CREATED = "Seed of organization {} created version {} of {} {} with {} files";
    public static final String SEED_SET_UNCHANGED = "Seed of organization {}: {} {} unchanged";
    public static final String SEED_COMPOSITION_REFUSED = "Seed of organization {} discarded: {} composition violations";
    public static final String SEED_PARENT_FIXED = "Seed of organization {} refused: the pack {} has the parent {}, the request names {}";
    public static final String SEED_PACKS_WRITTEN = "Seed of organization {} wrote the packs of repository {}";
    public static final String SEED_RULE_UNSCOPED = "Seed of organization {} refused: {} rules of mounted packs are not scoped to their folders";
    public static final String SEED_ITEMS_LOCKED = "Seed of organization {}: pack {} stores {} items its ancestors lock";
    public static final String SEED_COMPLETED = "Seed run {} of organization {} stored: {}, {} releases";
}
