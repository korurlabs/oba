package com.korur.oba.apps.kaya.render.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.FileRefusal;
import com.korur.oba.apps.kaya.render.dto.embedded.PathMappingCount;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolMapping;
import com.korur.oba.apps.kaya.render.enums.EntityKind;

/**
 * Renders a version's file tree from the stored kinds: every row writes its blob bytes unchanged at
 * the delivery path of its kind, and nothing is rebuilt from the index columns. Each skill also
 * renders its link .claude/skills/name and its parts at their link paths, so a part two skills share
 * appears in both directories.
 *
 * It reads each kind through that kind's Query service, for example
 * {@link com.korur.oba.apps.kaya.rule.service.RuleQueryService} and
 * {@link com.korur.oba.apps.kaya.skill.service.SkillQueryService}, and the bytes through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}; it writes nothing.
 *
 * Called by {@link com.korur.oba.apps.kaya.version.service.VersionQueryService} to render a
 * {@link com.korur.oba.apps.kaya.version.entity.Version}, by
 * {@link com.korur.oba.apps.kaya.seed.service.SeedService} to screen and count the files of a seed,
 * and by {@link RenderService} for the base of a change. The history of one file renders only that
 * path through {@link #renderPath}. The path mapping page reads {@link #getPathMappings} and
 * {@link #getToolMappings}.
 */
public interface RenderQueryService {

    /**
     * Renders one version.
     *
     * @param organizationId  the organization of the version
     * @param versionId       the version
     * @return every {@link RenderedFile} of the version, sorted by path
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException NOT_VERSIONED_RESOURCE_FOUND
     *         when a row references a blob the organization does not store
     */
    List<RenderedFile> render(UUID organizationId, UUID versionId);

    /**
     * Renders one path in several versions of one owner, reading only the kind that the path
     * classifies to and loading the blobs of all versions in one query, so that the history of a
     * file does not render every version whole. Each entry equals the entry of {@link #render} at
     * that path.
     *
     * @param organizationId  the organization of the versions
     * @param versionIds      the versions, each of the same pack
     * @param path            the path relative to the repository root
     * @return the {@link RenderedFile} at the path by version id; a version that does not deliver
     *         the path, an unknown version and a version of another organization have no entry
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException NOT_VERSIONED_RESOURCE_FOUND
     *         when the row at the path references a blob the organization does not store
     */
    Map<UUID, RenderedFile> renderPath(UUID organizationId, Collection<UUID> versionIds, String path);

    /**
     * Finds every file of a tree that {@link RenderService#parse} would refuse for what the file
     * holds, without storing anything, so that a caller can leave those files out and store the
     * rest. A skill directory whose files do not parse is refused whole, with its link; a link
     * without its skill directory and a second subagent of one name are refused too. The delivery
     * rules of {@link com.korur.oba.apps.kaya.validation.service.ValidationQueryService} are left to
     * the caller.
     *
     * @param files           every {@link SourceFile} of the tree, no removal, each path once
     * @return one {@link FileRefusal} per refused path, in the order of the files
     */
    List<FileRefusal> screen(List<SourceFile> files);

    /**
     * Counts the entities a tree is stored as, one per file: a skill by its SKILL.md and each other
     * file of a skill directory as its part.
     *
     * @param files           the {@link SourceFile}s, each of a path {@link #screen} accepts
     * @return the count of every {@link EntityKind}, zero included, in the enum's order
     */
    Map<EntityKind, Integer> countEntities(List<SourceFile> files);

    /**
     * Lists where every kind lives, the rows of
     * {@link com.korur.oba.apps.kaya.render.helper.DeliveryPaths#mappings}, each with the number of
     * files the given versions deliver at it. It reads the kind rows {@link #render} reads, without
     * a blob, and counts a row per agents file placement, per folder and, for a skill, per link: a
     * part two skills share counts once per skill, as it is delivered once per skill, and the skill
     * link row counts the skills. Counts are taken before composition, so a rule two owners deliver
     * counts in both.
     *
     * @param organizationId the organization of the versions
     * @param versionIds     the versions of the scope; empty for no scope
     * @return one {@link PathMappingCount} per row, in the order of the rows; the counts are null
     *         when no version is given, and an unknown version or one of another organization adds 0
     */
    List<PathMappingCount> getPathMappings(UUID organizationId, Collection<UUID> versionIds);

    /**
     * Lists, per AI tool, every path it reads as
     * {@link com.korur.oba.apps.kaya.render.helper.ToolPaths} describes it, each with what the given
     * versions deliver of it. The counts are those of {@link #getPathMappings}, read once for every
     * tool, and the state of a read follows from the counts of its link, its source and a regular
     * file at the read path.
     *
     * @param organizationId the organization of the versions
     * @param versionIds     the versions of the scope; empty for no scope
     * @return one {@link ToolMapping} per {@link com.korur.oba.apps.kaya.render.enums.AgentTool}, in
     *         the enum's order; state and count are null when no version is given, and an unknown
     *         version or one of another organization adds 0
     */
    List<ToolMapping> getToolMappings(UUID organizationId, Collection<UUID> versionIds);
}
