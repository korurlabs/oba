package com.korur.oba.shrine.organization.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Published when an organization is created, after the organization and its owner membership
 * are saved.
 *
 * Listeners run inside the creating transaction, so state every organization must start with,
 * such as its initial chat channels, is created atomically with it: a listener failure rolls
 * back the organization.
 *
 * @param organizationId the organization that was created
 */
public record OrganizationCreatedEvent(UUID organizationId) {
}
