package com.korur.oba.apps.kaya.agentsfile.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.dto.embedded.AgentsFileIndex;
import com.korur.oba.apps.kaya.agentsfile.entity.AgentsFile;
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;

/**
 * Writes the {@link AgentsFile}s of a version from the index the parse extracted. It parses nothing and
 * calls no other domain; the blobs it references are stored by the caller.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, so the index
 * rows commit together with the bytes they describe.
 */
public interface AgentsFileService {

    /**
     * Replaces every agents file of a version with the given ones.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param agentsFiles    the index of each agents file of the version; may be empty
     * @return the agents files written, in the given order
     */
    List<AgentsFile> replaceVersion(UUID organizationId, UUID versionId, List<AgentsFileIndex> agentsFiles);

    /**
     * Writes the {@link AgentsFile} of one saved file: the row at its placement is replaced, or the row is added
     * when the placement holds none. The old row is deleted rather than updated, because a row's blob never
     * changes.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param agentsFile     the index of the saved file
     * @return the agents file written
     */
    AgentsFile saveFile(UUID organizationId, UUID versionId, AgentsFileIndex agentsFile);

    /**
     * Deletes the {@link AgentsFile} at one placement of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param placement      the placement of the deleted file
     * @return whether the version had a agents file there
     */
    boolean deleteFile(UUID organizationId, UUID versionId, AgentsFilePlacement placement);
}
