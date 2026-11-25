package com.korur.oba.apps.kaya.ingest.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the ingest service.
 */
public final class IngestServiceLogConstant {

    private IngestServiceLogConstant() {
    }

    public static final String INGESTING_BATCH = "Ingesting batch of session {} from machine {}: {} events, {} handoffs, {} plans, usage {}";
    public static final String MACHINE_NAME_DIFFERENT = "Batch of session {} from machine {} reports another machine name: registered {}, reported {}";
    public static final String BATCH_EMPTY = "Refused batch of session {} from machine {}: no events and no usage";
    public static final String EVENT_INVALID = "Refused batch of session {} from machine {}: event {} has an invalid {}";
    public static final String RATE_LIMITED = "Refused batch of session {}: token {} sent more than {} batches in a minute";
    public static final String IDENTITY_CONFLICT = "Session {} reported a git email of user {}, not of token user {}";
    public static final String GIT_EMAIL_UNKNOWN = "Refused batch of session {} from machine {}: no user of organization {} holds its git email";
    public static final String GIT_EMAIL_IGNORED = "Ignored git author email of session {}: not an email address";
    public static final String NO_SESSION_FOR_USAGE_ONLY = "Session {} of organization {} is not recorded yet and the batch carries no events; no session created";
    public static final String BATCH_INGESTED = "Ingested batch of session {} from machine {}, acknowledged seq {}";
}
