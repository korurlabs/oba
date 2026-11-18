package com.korur.oba.apps.kaya.session.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;

// 4. Internal

/**
 * What the stored events of one session add up to: the counts and times the session row shows.
 */
public interface SessionEventAggregateProjection {

    Long getEventCount();

    Long getMaxSeq();

    Instant getFirstAt();

    Instant getLastAt();
}
