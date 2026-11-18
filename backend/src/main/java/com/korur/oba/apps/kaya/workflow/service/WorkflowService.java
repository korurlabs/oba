package com.korur.oba.apps.kaya.workflow.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.workflow.dto.embedded.WorkflowIndex;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;

/**
 * Writes the {@link Workflow}s of a version from the index the parse extracted. It parses nothing and calls
 * no other domain; the blobs it references are stored by the caller.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, so the index
 * rows commit together with the bytes they describe. Replacing the workflows also removes the skill
 * links to the old rows, so the caller writes the skills of the version after them.
 */
public interface WorkflowService {

    /**
     * Replaces every workflow of a version with the given ones.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param workflows      the index of each workflow file of the version; may be empty
     * @return the workflows written, in the given order, so the caller can refer to their ids
     */
    List<Workflow> replaceVersion(UUID organizationId, UUID versionId, List<WorkflowIndex> workflows);

    /**
     * Writes the {@link Workflow} of one saved file: the row at its path is replaced, or the row is added
     * when the path holds none. The old row is deleted rather than updated, because a row's blob never
     * changes.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param workflow       the index of the saved file
     * @return the workflow written
     */
    Workflow saveFile(UUID organizationId, UUID versionId, WorkflowIndex workflow);

    /**
     * Deletes the {@link Workflow} at one path of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path of the deleted file
     * @return whether the version had a workflow there
     */
    boolean deleteFile(UUID organizationId, UUID versionId, String relativePath);

    /**
     * Adds one {@link Workflow} with no path, delivered only inside skills, as a new part a skill can
     * link. It replaces nothing: parts are found by id, and two skills share one row by linking the
     * same id.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param workflow       the index of the part
     * @return the workflow written
     */
    Workflow savePart(UUID organizationId, UUID versionId, WorkflowIndex workflow);

    /**
     * Deletes the given parts of a version, those no skill links any more; the database removes any
     * link left to them. A row with a path is a delivered file and is never deleted here.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param partIds        the ids of the parts; may be empty
     * @return the number of parts deleted
     */
    int deleteParts(UUID organizationId, UUID versionId, Collection<UUID> partIds);
}
