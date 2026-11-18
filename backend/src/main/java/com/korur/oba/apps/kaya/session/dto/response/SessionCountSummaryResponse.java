package com.korur.oba.apps.kaya.session.dto.response;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * The sessions of one user or one machine in numbers, for the user and machine lists.
 *
 * @param id          the user or machine
 * @param sessions       its sessions
 * @param lastEventAt the last event of its newest session; null without sessions
 */
public record SessionCountSummaryResponse(
        UUID id,
        long sessions,
        Instant lastEventAt
) {
}
