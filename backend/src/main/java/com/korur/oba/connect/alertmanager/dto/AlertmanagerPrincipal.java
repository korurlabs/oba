package com.korur.oba.connect.alertmanager.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The caller of the Alertmanager webhook: an Alertmanager whose bearer token
 * matched. The organization is the one configured with that token, so a
 * notification never chooses the organization its alerts land in.
 *
 * @param organizationId the organization bound to the matched token
 */
public record AlertmanagerPrincipal(UUID organizationId) {
}
