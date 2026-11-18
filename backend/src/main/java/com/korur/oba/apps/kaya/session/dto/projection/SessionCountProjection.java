package com.korur.oba.apps.kaya.session.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * The sessions of one user or machine in numbers: how many, and the last event of the newest.
 */
public interface SessionCountProjection {

    UUID getId();

    Long getSessions();

    Instant getLastEventAt();
}
