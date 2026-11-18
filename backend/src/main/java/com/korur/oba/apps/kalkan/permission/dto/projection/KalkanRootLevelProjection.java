package com.korur.oba.apps.kalkan.permission.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;

/**
 * The level of one live grant of a member on one root page.
 */
public interface KalkanRootLevelProjection {

    UUID getRootId();

    KalkanLevel getLevel();
}
