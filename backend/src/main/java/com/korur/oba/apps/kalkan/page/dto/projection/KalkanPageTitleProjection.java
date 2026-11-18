package com.korur.oba.apps.kalkan.page.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;

/**
 * The title of one live translation, read without its body for the tree.
 */
public interface KalkanPageTitleProjection {

    UUID getPageId();

    KalkanLocale getLocale();

    String getTitle();
}
