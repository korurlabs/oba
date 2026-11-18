package com.korur.oba.apps.kaya.repository.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Status of a repository. The sessions of an ARCHIVED repository stay readable, but ingest for it
 * answers 422 KAY_VAL_002, so a recorder stops sending new sessions.
 */
public enum RepositoryStatus {
    ACTIVE,
    ARCHIVED
}
