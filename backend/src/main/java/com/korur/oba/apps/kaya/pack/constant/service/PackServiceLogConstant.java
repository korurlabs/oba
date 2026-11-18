package com.korur.oba.apps.kaya.pack.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the pack services, the not-found and refusal lines included.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PackServiceLogConstant {

    private PackServiceLogConstant() {
    }

    public static final String LISTING_PACKS = "Listing the packs of organization {}";
    public static final String LOADING_PACKS = "Loading {} packs of organization {}";
    public static final String PACK_NOT_FOUND = "Pack {} not found in organization {}";
    public static final String CREATING_PACK = "User {} creating pack {} under parent {} in organization {}";
    public static final String PACK_CREATED = "Audit: created pack {} ({}) under parent {} in organization {}";
    public static final String PACK_CHAIN_BROKEN = "Pack chain of {} in organization {} stopped at {} after {} packs: the parent links loop or leave the organization";
    public static final String PACK_NAME_TAKEN = "Refused pack name {}: organization {} already has a pack of that name";
    public static final String UPDATING_PACK = "User {} updating pack {} to name {} in organization {}";
    public static final String PACK_UPDATE_UNCHANGED = "Pack {} of organization {} already has that name and description; nothing written";
    public static final String PACK_RENAME_REFUSED = "Refused rename of pack {} of organization {}: the pack is in use ({})";
    public static final String PACK_UPDATED = "Audit: updated pack {} of organization {}, name {} to {}, description changed {}";
    public static final String PACK_CURRENT_VERSION_SET = "Pack {} of organization {} now delivers version {}";
    public static final String PACK_REQUIRED_SET = "Audit: pack {} of organization {} set required {}";
}
