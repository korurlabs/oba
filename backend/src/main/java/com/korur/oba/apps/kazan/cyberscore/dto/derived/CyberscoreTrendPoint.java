package com.korur.oba.apps.kazan.cyberscore.dto.derived;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.LocalDate;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;

/**
 * Score an organization held at the end of one business day, built by
 * {@link com.korur.oba.apps.kazan.cyberscore.service.CyberscoreTrendService}.
 *
 * @param date            business day
 * @param scorePercentage score percentage of that day
 * @param scoreLevel      band of that day's score
 */
public record CyberscoreTrendPoint(LocalDate date, int scorePercentage, CyberscoreLevel scoreLevel) {
}
