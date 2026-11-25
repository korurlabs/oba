package com.korur.oba.apps.kazan.cyberscore.dto.derived;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;

/**
 * Result of scoring a set of checks with
 * {@link com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCalculateService}.
 *
 * @param scorable            whether at least one check had an outcome that counts
 * @param scorePercentage     weighted score from 0 to 100; 0 when not scorable
 * @param scoreLevel          band of the percentage; UNKNOWN when not scorable
 * @param breakdownByCategory percentage per category name, only for categories with a scorable check
 */
public record CyberscoreCalculation(
        boolean scorable,
        int scorePercentage,
        CyberscoreLevel scoreLevel,
        Map<String, Integer> breakdownByCategory
) {
}
