package com.korur.oba.apps.kaya.render.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;

/**
 * Stores files into a version: each file's exact bytes become its blob, and the typed index columns
 * and relations of its kind are extracted from it in the same transaction. A file that cannot be
 * read, breaks a delivery rule or holds a value its table refuses throws INVALID_FILE; a path no kind
 * accepts throws NOT_DELIVERABLE. Either rolls the whole call back, so the stored files and their
 * index stay as they were.
 *
 * It writes the rows of each kind through that kind's command service, for example
 * {@link com.korur.oba.apps.kaya.rule.service.RuleService} and
 * {@link com.korur.oba.apps.kaya.skill.service.SkillService}, stores the bytes with
 * {@link com.korur.oba.apps.kaya.blob.service.BlobService}, renders the base of a change with
 * {@link RenderQueryService} and checks the files with
 * {@link com.korur.oba.apps.kaya.validation.service.ValidationQueryService}.
 *
 * Called only by {@link com.korur.oba.apps.kaya.version.service.VersionService}, which owns the
 * {@link com.korur.oba.apps.kaya.version.entity.Version} the files belong to; it joins the caller's
 * transaction, so a version and its files commit together.
 */
public interface RenderService {

    /**
     * Stores a whole file tree, replacing every kind row of the version.
     *
     * @param organizationId  the organization of the version
     * @param versionId       the version
     * @param files           every {@link SourceFile} of the tree, no removal, each path once
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_027
     *         INVALID_FILE or KAY_CFG_028 NOT_DELIVERABLE, naming the path and the reason
     */
    void parse(UUID organizationId, UUID versionId, List<SourceFile> files);

    /**
     * Stores a whole file tree as {@link #parse} does, as a change of a base version: a file whose
     * path the base also holds may keep a size limit the base's file already broke, as long as it
     * does not grow in what that limit measures.
     *
     * @param organizationId  the organization of the version
     * @param versionId       the version
     * @param files           every {@link SourceFile} of the tree, no removal, each path once
     * @param base            every {@link RenderedFile} of the base version with its content; empty
     *                        for a version without a base
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_027
     *         INVALID_FILE or KAY_CFG_028 NOT_DELIVERABLE, naming the path and the reason
     */
    void parseChange(UUID organizationId, UUID versionId, List<SourceFile> files,
                     List<RenderedFile> base);

    /**
     * Stores a whole file tree read from a repository as it is today, as {@link #parse} does, except
     * that a file over a size limit is stored: an import mirrors the repository, and its caller
     * reports those limits as warnings.
     *
     * @param organizationId  the organization of the version
     * @param versionId       the version
     * @param files           every {@link SourceFile} of the tree, no removal, each path once
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_027
     *         INVALID_FILE or KAY_CFG_028 NOT_DELIVERABLE, naming the path and the reason
     */
    void parseImported(UUID organizationId, UUID versionId, List<SourceFile> files);

    /**
     * Stores or removes one file and refreshes only the rows it touches. A file of a skill directory
     * re-parses that skill; a saved part another skill also links is relinked there, so both skills
     * render the new bytes. Removing SKILL.md removes the whole skill.
     *
     * @param organizationId  the organization of the version
     * @param versionId       the version
     * @param file            the {@link SourceFile} to store, or a removal
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_027
     *         INVALID_FILE or KAY_CFG_028 NOT_DELIVERABLE, naming the path and the reason
     */
    void parseOne(UUID organizationId, UUID versionId, SourceFile file);

    /**
     * Stores or removes one file as {@link #parseOne} does, as a change of a base version: the file
     * may keep a size limit the base's file at the same path already broke, as long as it does not
     * grow in what that limit measures.
     *
     * @param organizationId  the organization of the version
     * @param versionId       the version
     * @param file            the {@link SourceFile} to store, or a removal
     * @param base            every {@link RenderedFile} of the base version with its content; empty
     *                        for a version without a base
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_027
     *         INVALID_FILE or KAY_CFG_028 NOT_DELIVERABLE, naming the path and the reason
     */
    void parseOneChange(UUID organizationId, UUID versionId, SourceFile file,
                        List<RenderedFile> base);
}
