package com.korur.oba.apps.kalkan.page.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The place and delete state of a page, read whether it is deleted or not, for a restore.
 */
public interface KalkanPageStateProjection {

    UUID getId();

    UUID getParentId();

    UUID getRootId();

    String getSlug();

    Boolean getDeleted();

    /**
     * Whether the parent is deleted; false for a root page.
     */
    Boolean getParentDeleted();
}
