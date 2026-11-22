package com.korur.oba.apps.kalkan.page.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * The top page of one delete, with the path it had and the number of pages the same delete took
 * below it.
 */
public interface KalkanPageTrashProjection {

    UUID getId();

    /** The root whose grants decide who may restore this page: the page itself when it is a root. */
    UUID getAccessRootId();

    /** Whether the page is a root page, which only the root's managers restore. */
    Boolean getRoot();

    String getPath();

    Instant getDeletedAt();

    UUID getDeletedById();

    Long getDescendantCount();
}
