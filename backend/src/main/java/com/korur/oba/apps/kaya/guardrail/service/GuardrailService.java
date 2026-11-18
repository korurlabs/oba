package com.korur.oba.apps.kaya.guardrail.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.guardrail.dto.embedded.GuardrailIndex;
import com.korur.oba.apps.kaya.guardrail.entity.Guardrail;

/**
 * Writes the {@link Guardrail}s of a version from the index the parse extracted. It parses nothing and
 * calls no other domain; the blobs it references are stored by the caller.
 *
 * Called only by {@link com.korur.oba.apps.kaya.render.service.RenderService}, inside the transaction that
 * stores a version's files, so the index rows commit together with the bytes they describe.
 */
public interface GuardrailService {

    /**
     * Replaces every guardrail of a version with the given ones.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param guardrails     the index of each guardrail file of the version; may be empty
     * @return the guardrails written, in the given order
     */
    List<Guardrail> replaceVersion(UUID organizationId, UUID versionId, List<GuardrailIndex> guardrails);

    /**
     * Writes the {@link Guardrail} of one saved file: the row of its name is replaced, or the row is added
     * when the version holds none. The old row is deleted rather than updated, because a row's blob never
     * changes.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param guardrail      the index of the saved file
     * @return the guardrail written
     */
    Guardrail saveFile(UUID organizationId, UUID versionId, GuardrailIndex guardrail);

    /**
     * Deletes the {@link Guardrail} of one name in a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param name           the file name of the deleted file without .md
     * @return whether the version had a guardrail of that name
     */
    boolean deleteFile(UUID organizationId, UUID versionId, String name);
}
