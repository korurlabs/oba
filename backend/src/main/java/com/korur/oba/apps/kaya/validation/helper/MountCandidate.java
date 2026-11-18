package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A pack mounted in a folder of a repository as {@link CompositionRules} reads it, for the
 * namespace check.
 *
 * @param packId   the mounted pack
 * @param name     its name, the prefix of its namespace
 * @param rootPath the mount folder, relative to the repository root
 */
public record MountCandidate(UUID packId, String name, String rootPath) {
}
