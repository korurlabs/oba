package com.korur.oba.apps.kazan.cyberscore.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.derived.CyberscoreCalculation;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;

/**
 * Computes a cyberscore from checks. Pure: it reads and writes nothing.
 */
public interface CyberscoreCalculateService {

    /**
     * Scores the checks with the weighted formula. Each check whose outcome has
     * a factor adds its weight to the possible weight and weight times factor
     * to the achieved weight; the percentage is the rounded ratio. The same
     * runs per category, and a check without category counts only overall.
     *
     * @param checks the checks of one organization
     * @return the score, UNKNOWN with 0 when no check is scorable
     */
    CyberscoreCalculation calculate(List<CyberscoreCheck> checks);
}
