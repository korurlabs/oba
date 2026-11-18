package com.korur.oba.dojo.exception.enums.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.korur.oba.apps.kazan.dailyupdate.exception.ExceptionDomainDailyUpdateError;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kalkan.core.exception.enums.ExceptionDomainKalkanError;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.github.exception.ExceptionDomainGithubError;
import com.korur.oba.connect.intelligence.exception.ExceptionDomainIntelligenceError;
import com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError;
import com.korur.oba.connect.outbox.exception.ExceptionDomainOutboxError;
import com.korur.oba.connect.push.exception.ExceptionDomainPushError;
import com.korur.oba.connect.realtime.exception.ExceptionDomainRealtimeError;
import com.korur.oba.dojo.exception.enums.ExceptionApiError;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.enums.ExceptionStateError;
import com.korur.oba.dojo.exception.enums.ExceptionUpstreamError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.logging.exception.ExceptionDomainLoggingError;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.shrine.invitation.exception.ExceptionDomainInvitationError;
import com.korur.oba.shrine.onboarding.exception.ExceptionDomainOnboardingError;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.storage.exception.ExceptionDomainStorageError;

// 3. Java SDK
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionErrorConsistencyUnitTest {

    private static Stream<ExceptionError> allExceptionErrors() {
        List<ExceptionError> all = new ArrayList<>();
        addAll(all, ExceptionApiError.values());
        addAll(all, ExceptionInternalError.values());
        addAll(all, ExceptionNotFoundError.values());
        addAll(all, ExceptionSecurityError.values());
        addAll(all, ExceptionStateError.values());
        addAll(all, ExceptionUpstreamError.values());
        addAll(all, ExceptionValidationError.values());
        addAll(all, ExceptionDomainKazanError.values());
        addAll(all, ExceptionDomainKalkanError.values());
        addAll(all, ExceptionDomainOtakError.values());
        addAll(all, ExceptionDomainAzureError.values());
        addAll(all, ExceptionDomainGithubError.values());
        addAll(all, ExceptionDomainIntelligenceError.values());
        addAll(all, ExceptionDomainNotificationError.values());
        addAll(all, ExceptionDomainOutboxError.values());
        addAll(all, ExceptionDomainPushError.values());
        addAll(all, ExceptionDomainDailyUpdateError.values());
        addAll(all, ExceptionDomainRealtimeError.values());
        addAll(all, ExceptionDomainLoggingError.values());
        addAll(all, ExceptionDomainIdentityError.values());
        addAll(all, ExceptionDomainInvitationError.values());
        addAll(all, ExceptionDomainOnboardingError.values());
        addAll(all, ExceptionDomainOrganizationError.values());
        addAll(all, ExceptionDomainStorageError.values());
        return all.stream();
    }

    private static <E extends ExceptionError> void addAll(List<ExceptionError> target, E[] values) {
        for (E value : values) {
            target.add(value);
        }
    }

    @Nested
    class CodePrefixMatchesDeclaredCategory {

        @ParameterizedTest
        @MethodSource("com.korur.oba.dojo.exception.enums.unit.ExceptionErrorConsistencyUnitTest#allExceptionErrors")
        void errorCodePrefix_matchesDeclaredCategory(ExceptionError error) {
            String code = error.getCode();
            String[] parts = code.split("_");
            assertTrue(parts.length >= 3,
                    "Error code must follow DOMAIN_CATEGORY_NNN format: " + code);

            String codeCategoryPrefix = parts[1];
            String declaredCategory = error.getCategory().name();

            assertEquals(declaredCategory, codeCategoryPrefix,
                    "Error code " + code + " has prefix '" + codeCategoryPrefix
                            + "' but declared category '" + declaredCategory + "'");
        }
    }

    @Nested
    class EnumConstantNameStartsWithCategory {

        private static Stream<ExceptionError> domainExceptionErrors() {
            return allExceptionErrors().filter(e -> e instanceof ExceptionDomainError);
        }

        @ParameterizedTest
        @MethodSource("domainExceptionErrors")
        void enumConstantName_startsWithCategoryPrefix(ExceptionError error) {
            String enumName = ((Enum<?>) error).name();
            String categoryPrefix = error.getCategory().name() + "_";

            assertTrue(enumName.startsWith(categoryPrefix),
                    "Enum constant '" + enumName + "' should start with '" + categoryPrefix + "'");
        }
    }

    @Nested
    class GlobalUniqueness {

        @Test
        void allErrorCodes_areGloballyUnique() {
            Map<String, String> seen = new HashMap<>();
            allExceptionErrors().forEach(error -> {
                String existing = seen.put(error.getCode(), ((Enum<?>) error).name());
                assertNull(existing,
                        "Duplicate error code '" + error.getCode() + "' found in both '"
                                + existing + "' and '" + ((Enum<?>) error).name() + "'");
            });
        }
    }

    @Nested
    class CategoryIsNotNull {

        @ParameterizedTest
        @MethodSource("com.korur.oba.dojo.exception.enums.unit.ExceptionErrorConsistencyUnitTest#allExceptionErrors")
        void category_isNeverNull(ExceptionError error) {
            assertNotNull(error.getCategory(),
                    "Category must not be null for error code: " + error.getCode());
        }
    }

    @Nested
    class MessageIsNotBlank {

        @ParameterizedTest
        @MethodSource("com.korur.oba.dojo.exception.enums.unit.ExceptionErrorConsistencyUnitTest#allExceptionErrors")
        void message_isNeverBlank(ExceptionError error) {
            assertNotNull(error.getMessage(),
                    "Message must not be null for error code: " + error.getCode());
            assertFalse(error.getMessage().isBlank(),
                    "Message must not be blank for error code: " + error.getCode());
        }
    }
}
