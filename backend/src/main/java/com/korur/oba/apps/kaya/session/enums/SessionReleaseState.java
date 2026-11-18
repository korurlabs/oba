package com.korur.oba.apps.kaya.session.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The state of the release a session ran under. The CLI reports FRESH (the session started
 * on the current release), CACHED (Kaya was unreachable and the last verified release was used),
 * STALE (a newer release waits for a restart) or NONE (no verified release on the machine).
 * UNVERIFIED is set by Kaya alone, when the reported number and manifest hash match no release of
 * the session's repository.
 */
public enum SessionReleaseState {
    FRESH,
    CACHED,
    STALE,
    NONE,
    UNVERIFIED
}
