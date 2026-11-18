package com.korur.oba.apps.kaya.hook.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.hook.dto.embedded.HookIndex;
import com.korur.oba.apps.kaya.hook.entity.Hook;

/**
 * Writes the {@link Hook}s of a version from the index the parse extracted. It parses nothing and calls
 * no other domain; the blobs it references are stored by the caller.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, so the index
 * rows commit together with the bytes they describe.
 */
public interface HookService {

    /**
     * Replaces every hook of a version with the given ones.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param hooks          the index of each hook file of the version; may be empty
     * @return the hooks written, in the given order, so the caller can refer to their ids
     */
    List<Hook> replaceVersion(UUID organizationId, UUID versionId, List<HookIndex> hooks);

    /**
     * Writes the {@link Hook} of one saved file: the row at its path is replaced, or the row is added
     * when the path holds none. The old row is deleted rather than updated, because a row's blob never
     * changes.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param hook           the index of the saved file
     * @return the hook written
     */
    Hook saveFile(UUID organizationId, UUID versionId, HookIndex hook);

    /**
     * Deletes the {@link Hook} at one path of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path of the deleted file
     * @return whether the version had a hook there
     */
    boolean deleteFile(UUID organizationId, UUID versionId, String relativePath);
}
