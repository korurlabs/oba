package com.korur.oba.shrine.organization.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One active member of an organization with the user fields needed to name them,
 * read without loading the membership or user entities.
 *
 * The role is the OrganizationMemberRole name as text, so callers can filter on it
 * with OrganizationMemberRole.valueOf.
 */
public interface OrganizationMemberSummaryProjection {

    UUID getUserId();

    String getEmail();

    String getFirstName();

    String getLastName();

    String getRole();
}
