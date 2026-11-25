package com.korur.oba.apps.kazan.cyberscore.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreResponse;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreTrendPointResponse;

/**
 * Read service of an organization's cyberscore and its trend.
 */
public interface CyberscoreQueryService {

    /**
     * Returns the organization's current score. An organization that was
     * never scored gets the not-yet-scored shape (0, UNKNOWN, no breakdown, no
     * scorable check, no calculation time), and nothing is written.
     *
     * @param organizationId the organization of the request
     * @return the current score
     */
    CyberscoreResponse getCurrent(UUID organizationId);

    /**
     * Returns the organization's score per business day.
     *
     * @param organizationId the organization of the request
     * @param days           window length in days, clamped to 1 to 365
     * @return the trend points, oldest first; empty when never scored
     */
    List<CyberscoreTrendPointResponse> getTrend(UUID organizationId, int days);
}
