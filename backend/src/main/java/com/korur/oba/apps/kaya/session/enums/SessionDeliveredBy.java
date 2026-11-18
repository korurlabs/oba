package com.korur.oba.apps.kaya.session.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * What activated the release of a session: the launcher before Claude Code started, or the
 * SessionStart hook when the session started without the launcher.
 */
public enum SessionDeliveredBy {
    LAUNCHER,
    HOOK
}
