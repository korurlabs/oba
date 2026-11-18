package com.korur.oba.apps.kaya.release.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One pack a repository composes, as {@link Composer} reads it: at the repository root, or mounted
 * in a folder.
 *
 * @param packId   the pack
 * @param rootPath the mount folder, relative to the repository root; null for a pack composed at
 *                 the root
 */
public record PackMount(UUID packId, String rootPath) {

    /**
     * A pack composed at the repository root.
     *
     * @param packId the pack
     * @return the mount without a folder
     */
    public static PackMount atRoot(UUID packId) {
        return new PackMount(packId, null);
    }

    /**
     * Whether the pack is mounted in a folder.
     *
     * @return true when it has a mount folder
     */
    public boolean mounted() {
        return rootPath != null;
    }
}
