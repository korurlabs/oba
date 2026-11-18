package com.korur.oba.shrine.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

/**
 * Centralizes anonymization logic for GDPR-compliant soft-delete flows.
 *
 * Replaces PII with placeholder values and sets the entity status to DISABLED
 * before the soft-delete flag is applied.
 */
@Component
@RequiredArgsConstructor
public class AnonymizationHelper {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AnonymizationHelper.class);

    private final LoggingService loggingService;

    /**
     * Anonymizes a user entity by replacing PII with placeholder values.
     * Sets email to a deterministic anonymized address, clears profile fields,
     * removes preferences and default organization, and sets status to DISABLED.
     *
     * @param user the user entity to anonymize
     */
    public void anonymizeUser(User user) {
        user.setEmail("deleted-" + user.getId() + "@anonymized.local");
        user.getProfile().setFirstName("Deleted");
        user.getProfile().setLastName("User");
        user.getProfile().setPhoneNumber(null);
        user.setUserPreferences(null);
        user.setDefaultOrganization(null);
        user.setStatus(UserStatus.DISABLED);

        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                "User anonymized with database ID: {}", user.getId());
    }

    /**
     * Anonymizes an organization entity by replacing identifying data
     * with placeholder values and setting status to DISABLED.
     *
     * @param organization the organization entity to anonymize
     */
    public void anonymizeOrganization(Organization organization) {
        organization.setName("Deleted Organization");
        organization.setDescription(null);
        organization.setWebsite(null);
        organization.setStatus(OrganizationStatus.DISABLED);

        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                "Organization anonymized with database ID: {}", organization.getId());
    }
}
