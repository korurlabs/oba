package com.korur.oba.apps.kaya.subagent.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.subagent.dto.embedded.SubagentIndex;
import com.korur.oba.apps.kaya.subagent.entity.Subagent;

/**
 * Writes the {@link Subagent}s of a version from the index the parse extracted. It parses nothing and
 * calls no other domain; the blobs it references are stored by the caller.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, so the index
 * rows commit together with the bytes they describe.
 */
public interface SubagentService {

    /**
     * Replaces every subagent of a version with the given ones.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param subagents      the index of each subagent file of the version; may be empty
     * @return the subagents written, in the given order
     */
    List<Subagent> replaceVersion(UUID organizationId, UUID versionId, List<SubagentIndex> subagents);

    /**
     * Writes the {@link Subagent} of one saved file: the row at its path is replaced, or the row is added
     * when the path holds none. The old row is deleted rather than updated, because a row's blob never
     * changes.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param subagent       the index of the saved file
     * @return the subagent written
     */
    Subagent saveFile(UUID organizationId, UUID versionId, SubagentIndex subagent);

    /**
     * Deletes the {@link Subagent} at one path of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path of the deleted file
     * @return whether the version had a subagent there
     */
    boolean deleteFile(UUID organizationId, UUID versionId, String relativePath);
}
