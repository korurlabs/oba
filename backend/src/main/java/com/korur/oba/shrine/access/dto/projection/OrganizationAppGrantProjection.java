package com.korur.oba.shrine.access.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One app that an organization grants to a user through a grant that is not deleted, read without
 * loading the grant entities.
 *
 * The app is the {@link com.korur.oba.shrine.access.enums.App} name as text, because the app column is
 * a PostgreSQL enum that a native query reads as text.
 */
public interface OrganizationAppGrantProjection {

    UUID getOrganizationId();

    String getApp();
}
