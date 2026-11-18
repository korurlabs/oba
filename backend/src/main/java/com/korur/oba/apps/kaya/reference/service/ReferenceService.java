package com.korur.oba.apps.kaya.reference.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.reference.dto.embedded.ReferenceIndex;
import com.korur.oba.apps.kaya.reference.entity.Reference;

/**
 * Writes the {@link Reference}s of a version from the index the parse extracted. It parses nothing and calls
 * no other domain; the blobs it references are stored by the caller.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, so the index rows
 * commit together with the bytes they describe. Replacing the references also removes the skill links to the
 * old rows, so the caller writes the skills of the version after them.
 */
public interface ReferenceService {

    /**
     * Replaces every reference of a version with the given ones.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param references the index of each reference file of the version; may be empty
     * @return the references written, in the given order, so the caller can link skills to their ids
     */
    List<Reference> replaceVersion(UUID organizationId, UUID versionId, List<ReferenceIndex> references);

    /**
     * Adds one {@link Reference} as a new part a skill can link. It replaces nothing: parts are found
     * by id, and two skills share one row by linking the same id.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param reference      the index of the part
     * @return the reference written
     */
    Reference savePart(UUID organizationId, UUID versionId, ReferenceIndex reference);

    /**
     * Deletes the given parts of a version, those no skill links any more; the database removes any
     * link left to them.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param partIds        the ids of the parts; may be empty
     * @return the number of parts deleted
     */
    int deleteParts(UUID organizationId, UUID versionId, Collection<UUID> partIds);
}
