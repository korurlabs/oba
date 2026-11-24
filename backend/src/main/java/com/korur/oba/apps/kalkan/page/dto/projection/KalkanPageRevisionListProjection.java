package com.korur.oba.apps.kalkan.page.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;

/**
 * One revision of a page, read without its body for the history list.
 */
public interface KalkanPageRevisionListProjection {

    UUID getId();

    KalkanLocale getLocale();

    String getTitle();

    /**
     * The page version the write produced.
     */
    Long getPageVersion();

    Instant getCreatedAt();

    UUID getCreatedById();
}
