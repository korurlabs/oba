package com.korur.oba.apps.kaya.template.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.template.dto.embedded.TemplateIndex;
import com.korur.oba.apps.kaya.template.entity.Template;

/**
 * Writes the {@link Template}s of a version from the index the parse extracted. It parses nothing and
 * calls no other domain; the blobs it references are stored by the caller.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, so the index
 * rows commit together with the bytes they describe.
 */
public interface TemplateService {

    /**
     * Replaces every template of a version with the given ones.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param templates      the index of each template file of the version; may be empty
     * @return the templates written, in the given order
     */
    List<Template> replaceVersion(UUID organizationId, UUID versionId, List<TemplateIndex> templates);

    /**
     * Writes the {@link Template} of one saved file: the row at its path is replaced, or the row is added
     * when the path holds none. The old row is deleted rather than updated, because a row's blob never
     * changes.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param template       the index of the saved file
     * @return the template written
     */
    Template saveFile(UUID organizationId, UUID versionId, TemplateIndex template);

    /**
     * Deletes the {@link Template} at one path of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path of the deleted file
     * @return whether the version had a template there
     */
    boolean deleteFile(UUID organizationId, UUID versionId, String relativePath);
}
