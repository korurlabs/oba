package com.korur.oba.apps.kazan.cyberscore.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

/**
 * Builds the day-by-day cyberscore trend of an organization from its snapshots.
 */
public interface CyberscoreTrendService {

    /**
     * Builds one point per business day that has a score, oldest first. The
     * first day of the window carries the score held before it, the last
     * snapshot of a day wins, and today is the current score row. Snapshots
     * without a score band are left out.
     *
     * @param organizationId the organization of the request
     * @param days           window length in days, clamped to 1 to 365
     * @return the trend; empty when the organization has no scored row
     */
    List<CyberscoreTrendPoint> buildTrend(UUID organizationId, int days);
}
