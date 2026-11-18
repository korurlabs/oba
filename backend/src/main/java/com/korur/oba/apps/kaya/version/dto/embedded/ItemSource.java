package com.korur.oba.apps.kaya.version.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The ancestor pack that delivers an item to a pack, the nearest one when several of its chain
 * give it, at its current version.
 *
 * @param packId    the ancestor pack
 * @param versionId the ancestor's current version
 */
public record ItemSource(UUID packId, UUID versionId) {
}
