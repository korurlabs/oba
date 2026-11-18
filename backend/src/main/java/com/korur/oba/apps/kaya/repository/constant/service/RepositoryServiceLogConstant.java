package com.korur.oba.apps.kaya.repository.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the repository services.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RepositoryServiceLogConstant {

    private RepositoryServiceLogConstant() {
    }

    public static final String LISTING_REPOSITORIES = "Listing the repositories of organization {}";
    public static final String REPOSITORY_NOT_FOUND = "Repository {} not found in organization {}";
    public static final String UPDATING_REPOSITORY = "User {} updating repository {} of organization {}";
    public static final String REPOSITORY_UPDATED = "Audit: user {} changed repository {} of organization {} from name '{}', ticket URL template '{}', status {} to name '{}', ticket URL template '{}', status {}";
    public static final String CONNECTING_REPOSITORY = "Connecting repository {} for organization {}";
    public static final String REMOTE_URL_INVALID = "Ingest for organization {} reported a remote URL that is not a git remote Kaya accepts";
    public static final String REPOSITORY_CONNECTED = "Audit: connected repository {} ({}) to organization {}";
    public static final String REPOSITORY_ARCHIVED = "Repository {} of organization {} is archived; ingest refused";
}
