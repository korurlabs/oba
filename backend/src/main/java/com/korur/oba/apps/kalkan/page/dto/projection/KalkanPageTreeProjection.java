package com.korur.oba.apps.kalkan.page.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One live page of the tree query, with the path of slugs from its root.
 */
public interface KalkanPageTreeProjection {

    UUID getId();

    UUID getParentId();

    UUID getRootId();

    String getSlug();

    String getPath();

    Integer getPosition();

    Boolean getCompliance();
}
