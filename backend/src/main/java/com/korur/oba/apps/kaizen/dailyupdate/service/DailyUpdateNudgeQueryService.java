package com.korur.oba.apps.kaizen.dailyupdate.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal

/**
 * Read operations for daily update nudges.
 */
public interface DailyUpdateNudgeQueryService {

    /**
     * Tells whether a member was already nudged in an organization on a
     * business day, by anyone.
     *
     * @param organizationId the organization
     * @param recipientId    the member
     * @param day            the business day on {@link com.korur.oba.dojo.time.BusinessCalendar}
     * @return true when a nudge exists
     */
    boolean isNudged(UUID organizationId, UUID recipientId, LocalDate day);
}
