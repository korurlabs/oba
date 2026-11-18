package com.korur.oba.apps.kaya.rule.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.rule.dto.embedded.RuleIndex;
import com.korur.oba.apps.kaya.rule.entity.Rule;

/**
 * Writes the {@link Rule}s of a version and their {@link com.korur.oba.apps.kaya.rule.entity.RuleConvention}
 * links from the index the parse extracted. It parses nothing and calls no other domain; the blobs it
 * references are stored by the caller.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, so the index
 * rows commit together with the bytes they describe.
 */
public interface RuleService {

    /**
     * Replaces every rule of a version with the given ones, convention links included.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param rules          the index of each rule file of the version; may be empty
     * @return the rules written, in the given order
     */
    List<Rule> replaceVersion(UUID organizationId, UUID versionId, List<RuleIndex> rules);

    /**
     * Writes the {@link Rule} of one saved file: the row at its path is replaced, or the row is added
     * when the path holds none. The old row is deleted rather than updated, because a row's blob never
     * changes.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param rule           the index of the saved file
     * @return the rule written
     */
    Rule saveFile(UUID organizationId, UUID versionId, RuleIndex rule);

    /**
     * Deletes the {@link Rule} at one path of a version; the database removes its convention links with it.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path of the deleted file
     * @return whether the version had a rule there
     */
    boolean deleteFile(UUID organizationId, UUID versionId, String relativePath);
}
