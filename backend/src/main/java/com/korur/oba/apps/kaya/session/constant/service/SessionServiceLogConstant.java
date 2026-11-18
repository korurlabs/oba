package com.korur.oba.apps.kaya.session.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the session services.
 */
public final class SessionServiceLogConstant {

    private SessionServiceLogConstant() {
    }

    // --- Ingest ---
    public static final String SESSION_CREATED = "Created the session row of Claude Code session {} in organization {} by user {}";
    public static final String SESSION_OWNED_BY_OTHER_USER = "Refused: session {} of organization {} belongs to user {}, not to user {}";
    public static final String BATCH_APPLIED = "Applied batch to session {}: {} new events, {} in all, acknowledged seq {}";
    public static final String RELEASE_RECORDED = "Session {} runs under release {} ({}) with state {}, delivered by {}";
    public static final String RELEASE_NONE = "Session {} reported no verified release, delivered by {}";
    public static final String RELEASE_UNVERIFIED = "Session {} reported release {} with manifest {} and state {}, "
            + "which no release of repository {} matches";
    public static final String EVENTS_STORED = "Stored {} of {} events of the batch for session {}";
    public static final String LINKS_ADDED = "Added {} recorder links to session {}";
    public static final String SESSION_MISSING_AFTER_INSERT = "The session row was not found right after its insert";
    public static final String EVENTS_SERIALIZATION_FAILED = "Could not serialise the events of the batch for session {}";
    public static final String FIELD_TRUNCATED = "Cut {} of session {} to {} characters";


    // --- Reads ---
    public static final String LISTING_SESSIONS = "Listing the sessions of organization {} with {}";
    public static final String READING_FILTERS = "Reading the session filters of organization {}";
    public static final String READING_SESSION = "Reading session {} of organization {}";
    public static final String SESSION_NOT_FOUND = "Session {} not found in organization {}";
    public static final String LINK_NOT_FOUND = "Link {} not found on session {} of organization {}";

    // --- Links and delete ---
    public static final String LINK_REFERENCE_INVALID = "Refused {} link {} for session {}: rule {}";
    public static final String LINK_ADDED = "User {} added {} link {} to session {}";
    public static final String LINK_ALREADY_PRESENT = "Session {} already has the {} link {}; returned as it is";
    public static final String LINK_MISSING_AFTER_INSERT = "The %s link %s of session %s is missing right after its insert";
    public static final String LINK_REMOVED = "User {} removed link {} of session {}";
    public static final String SESSION_DELETED = "User {} deleted session {} (Claude Code session id {}) in organization {}";

    // --- Retention ---
    public static final String RETENTION_STARTED = "Session retention started";
    public static final String RETENTION_COMPLETED = "Session retention deleted {} sessions past their organization's retention";
    public static final String RETENTION_FAILED = "Session retention failed; the sessions stay until the next run";

    // --- Live stream ---
    public static final String STREAM_SUBSCRIBED = "User {} subscribed to the session stream of organization {}; {} open in the organization";
    public static final String STREAM_CLOSED = "A session stream of organization {} closed; {} open in the organization";
    public static final String STREAM_SEND_FAILED = "Could not send {} to a session stream of organization {}; the stream is closed";
    public static final String STREAM_COMPLETE_FAILED = "A session stream of organization {} had already ended and could not be completed";
    public static final String STREAMS_COMPLETED_ON_SHUTDOWN = "Completed {} open session streams on shutdown";
    public static final String STREAM_PUBLISHING = "Publishing session {} of organization {} to {} streams";
}
