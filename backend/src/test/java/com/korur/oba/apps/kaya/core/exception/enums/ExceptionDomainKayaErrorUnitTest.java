package com.korur.oba.apps.kaya.core.exception.enums;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The Kaya error catalogue, which the shared consistency test does not list. KAY_SEC_004 could not
 * join it as it stands: it is answered as a 429, so it declares the category API, which its SEC code
 * does not match.
 */
class ExceptionDomainKayaErrorUnitTest {

    @Test
    void secRateLimited_namesNoSingleLimitThatRefused() {
        // KAY_SEC_004 answers the budgets of a token, a user and an organization, the caps on reads at
        // once and the ingest budget; errorValues.limit and errorValues.policy name which one refused.
        String message = ExceptionDomainKayaError.SEC_RATE_LIMITED.getMessage().toLowerCase(Locale.ROOT);

        for (String limit : List.of("token", "machine", "user", "organization", "lesson", "batch")) {
            assertFalse(message.contains(limit), "KAY_SEC_004 names one limit: " + message);
        }
        assertFalse(message.isBlank());
    }
}
