package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.PathMapping;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileFormat;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

/**
 * The delivery path of every kind, in both directions: {@link #classify} maps a delivered path to its
 * {@link FileKind}, and the other methods build the path a stored row renders at. A path no kind
 * accepts is not delivered, which the caller refuses as NOT_DELIVERABLE. The Markdown kinds take .md
 * files only, because their index columns come from the frontmatter.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DeliveryPaths {

    // The kinds stored by a path below their folder whose index comes from the frontmatter.
    private static final List<FileKind> MARKDOWN_KINDS = List.of(
            FileKind.RULE, FileKind.ROLE, FileKind.WORKFLOW, FileKind.TEMPLATE, FileKind.SUBAGENT,
            FileKind.CONVENTION
    );

    // Built once from the same folders and file names classify reads; see mappings().
    private static final List<PathMapping> MAPPINGS = buildMappings();

    private DeliveryPaths() {
    }

    /**
     * Classifies a file and holds it to what its kind stores: only the link of a skill and the tool
     * links are symlinks, a link is never a file, a skill's link points at its own directory and a tool
     * link at its fixed target.
     *
     * @param file            the {@link SourceFile}, or a removal, which is only classified
     * @param mountRootPath   the mount folder whose AGENTS.md the file may be; null for a stored
     *                        version, whose files keep their repository paths
     * @return the delivery path of the file
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_028
     *         NOT_DELIVERABLE for a path no kind accepts or a symlink where a file belongs, and
     *         KAY_CFG_027 INVALID_FILE for a skill link or a tool link with another target
     */
    public static DeliveryPath requireDeliverable(SourceFile file, String mountRootPath) {
        DeliveryPath delivery = classify(file.path(), mountRootPath, file.isSymlink())
                .orElseThrow(() -> RenderRefusals.notDeliverable(String.valueOf(file.path()), RenderConstant.NOT_DELIVERABLE_REASON));
        if (file.isRemoval()) {
            return delivery;
        }
        boolean link = delivery.kind() == FileKind.SKILL_LINK || delivery.kind() == FileKind.TOOL_LINK;
        if (link != file.isSymlink()) {
            throw RenderRefusals.notDeliverable(file.path(), RenderConstant.SYMLINK_NOT_STORED_REASON);
        }
        if (delivery.kind() == FileKind.SKILL_LINK && !skillLinkTarget(delivery.skillName()).equals(file.symlinkTarget())) {
            throw RenderRefusals.invalidFile(file.path(), String.format(RenderConstant.SKILL_LINK_TARGET_REASON, delivery.skillName()));
        }
        String toolLinkTarget = delivery.kind() == FileKind.TOOL_LINK ? toolLinkTarget(delivery.toolLink()) : null;
        if (toolLinkTarget != null && !toolLinkTarget.equals(file.symlinkTarget())) {
            throw RenderRefusals.invalidFile(file.path(), String.format(RenderConstant.TOOL_LINK_TARGET_REASON, file.path(),
                    toolLinkTarget));
        }
        return delivery;
    }

    /**
     * Classifies a delivered path as a regular file would be: AGENTS.md is the agents file of the
     * root. See {@link #classify(String, String, boolean)}.
     *
     * @param path            the path relative to the repository root
     * @param mountRootPath   the mount folder whose AGENTS.md the file may be; null for a stored
     *                        version, whose files keep their repository paths
     * @return the kind and the path inside its folder; empty when no kind is delivered there
     */
    public static Optional<DeliveryPath> classify(String path, String mountRootPath) {
        return classify(path, mountRootPath, false);
    }

    /**
     * Classifies a delivered path. AGENTS.md is the only path two kinds share: as a symlink it is the
     * tool link to .agents/AGENTS.md, otherwise the agents file of the root. .claude/rules and
     * .claude/templates are tool links whatever the mode, so a regular file there is refused as a file
     * where a link belongs.
     *
     * @param path            the path relative to the repository root
     * @param mountRootPath   the mount folder whose AGENTS.md the file may be; null for a stored
     *                        version, whose files keep their repository paths
     * @param symlink         whether the path is delivered as a symlink
     * @return the kind and the path inside its folder; empty when no kind is delivered there
     */
    public static Optional<DeliveryPath> classify(String path, String mountRootPath, boolean symlink) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        Optional<ToolLinkName> toolLink = toolLinkOf(path);
        if (toolLink.isPresent() && (symlink || toolLink.get() != ToolLinkName.ROOT_AGENTS_FILE)) {
            return Optional.of(new DeliveryPath(path, FileKind.TOOL_LINK, null, null, null, toolLink.get()));
        }
        if (path.equals(RenderConstant.AGENTS_FILE)) {
            return Optional.of(agents(path, AgentsFilePlacement.ROOT));
        }
        if (path.equals(RenderConstant.AGENTS_DIRECTORY_FILE)) {
            return Optional.of(agents(path, AgentsFilePlacement.AGENTS_DIR));
        }
        if (mountRootPath != null && path.equals(mountAgentsFile(mountRootPath))) {
            return Optional.of(agents(path, AgentsFilePlacement.MOUNT_ROOT));
        }
        if (path.equals(RenderConstant.SETTINGS_FILE)) {
            return Optional.of(new DeliveryPath(path, FileKind.SETTINGS, null, null, null, null));
        }
        if (path.startsWith(RenderConstant.SKILLS_FOLDER)) {
            return skillFile(path);
        }
        if (path.startsWith(RenderConstant.CLAUDE_SKILLS_FOLDER)) {
            String name = path.substring(RenderConstant.CLAUDE_SKILLS_FOLDER.length());
            return name.isEmpty() || name.contains(RenderConstant.PATH_SEPARATOR)
                    ? Optional.empty()
                    : Optional.of(new DeliveryPath(path, FileKind.SKILL_LINK, null, name, null, null));
        }
        if (path.startsWith(RenderConstant.HOOKS_FOLDER)) {
            return inFolder(path, FileKind.HOOK, null);
        }
        // Before the Markdown kinds, so the rules folder that holds the guardrails never takes one.
        if (path.startsWith(RenderConstant.GUARDRAILS_FOLDER)) {
            return inFolder(path, FileKind.GUARDRAIL, RenderConstant.MARKDOWN_SUFFIX);
        }
        for (FileKind kind : MARKDOWN_KINDS) {
            if (path.startsWith(folderOf(kind))) {
                return inFolder(path, kind, RenderConstant.MARKDOWN_SUFFIX);
            }
        }
        return Optional.empty();
    }

    private static DeliveryPath agents(String path, AgentsFilePlacement placement) {
        return new DeliveryPath(path, FileKind.AGENTS_FILE, null, null, placement, null);
    }

    private static Optional<DeliveryPath> inFolder(String path, FileKind kind, String requiredSuffix) {
        String relativePath = path.substring(folderOf(kind).length());
        if (relativePath.isEmpty() || (requiredSuffix != null && !relativePath.endsWith(requiredSuffix))) {
            return Optional.empty();
        }
        return Optional.of(new DeliveryPath(path, kind, relativePath, null, null, null));
    }

    private static Optional<DeliveryPath> skillFile(String path) {
        String rest = path.substring(RenderConstant.SKILLS_FOLDER.length());
        int separator = rest.indexOf(RenderConstant.PATH_SEPARATOR);
        if (separator <= 0 || separator == rest.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new DeliveryPath(path, FileKind.SKILL, rest.substring(separator + 1),
                rest.substring(0, separator), null, null));
    }

    /**
     * The folder of a kind that is stored by its relative path.
     *
     * @param kind a kind with a folder: rule, guardrail, convention, role, workflow, template, hook or
     *             subagent
     * @return the folder from the repository root, ending with a slash
     * @throws IllegalArgumentException for a kind without a folder
     */
    public static String folderOf(FileKind kind) {
        return switch (kind) {
            case RULE -> RenderConstant.RULES_FOLDER;
            case GUARDRAIL -> RenderConstant.GUARDRAILS_FOLDER;
            case CONVENTION -> RenderConstant.CONVENTIONS_FOLDER;
            case ROLE -> RenderConstant.ROLES_FOLDER;
            case WORKFLOW -> RenderConstant.WORKFLOWS_FOLDER;
            case TEMPLATE -> RenderConstant.TEMPLATES_FOLDER;
            case HOOK -> RenderConstant.HOOKS_FOLDER;
            case SUBAGENT -> RenderConstant.SUBAGENTS_FOLDER;
            case AGENTS_FILE, SETTINGS, SKILL, SKILL_LINK, TOOL_LINK -> throw new IllegalArgumentException(kind.name());
        };
    }

    /**
     * The path an agents file renders at.
     *
     * @param placement       the placement
     * @param mountRootPath   the mount folder, needed for {@link AgentsFilePlacement#MOUNT_ROOT}
     * @return the path, or empty for the mount placement without a folder
     */
    public static Optional<String> agentsFile(AgentsFilePlacement placement, String mountRootPath) {
        return switch (placement) {
            case ROOT -> Optional.of(RenderConstant.AGENTS_FILE);
            case AGENTS_DIR -> Optional.of(RenderConstant.AGENTS_DIRECTORY_FILE);
            case MOUNT_ROOT -> mountRootPath == null ? Optional.empty() : Optional.of(mountAgentsFile(mountRootPath));
        };
    }

    /** The directory of a skill, ending with a slash, such as .agents/skills/kaizen/. */
    public static String skillDirectory(String name) {
        return RenderConstant.SKILLS_FOLDER + name + RenderConstant.PATH_SEPARATOR;
    }

    /** The link .claude/skills/name that makes Claude Code load the skill. */
    public static String skillLink(String name) {
        return RenderConstant.CLAUDE_SKILLS_FOLDER + name;
    }

    /** The target of the link of a skill, relative to the link. */
    public static String skillLinkTarget(String name) {
        return RenderConstant.SKILL_LINK_TARGET_PREFIX + name;
    }

    /** The path a tool link lies at, such as .claude/rules. */
    public static String toolLinkPath(ToolLinkName name) {
        return RenderConstant.TOOL_LINK_PATHS.get(name);
    }

    /** The target of a tool link, relative to the link's directory, such as ../.agents/rules. */
    public static String toolLinkTarget(ToolLinkName name) {
        return RenderConstant.TOOL_LINK_TARGETS.get(name);
    }

    /**
     * The tool link that lies at a path.
     *
     * @param path the path relative to the repository root
     * @return the link; empty when no tool link lies there
     */
    public static Optional<ToolLinkName> toolLinkOf(String path) {
        for (ToolLinkName name : ToolLinkName.values()) {
            if (toolLinkPath(name).equals(path)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    /**
     * Where every kind lives in a repository: one row per agents file placement, per kind with a
     * folder or a fixed file, for SKILL.md, per part of a skill directory, for the skill link and per
     * tool link, in the order a reader looks for them. The rows sit beside {@link #classify} and are built from the
     * same {@link RenderConstant} folders and file names, so the path mapping page and the delivery
     * share one source; a test classifies an example path of every row and expects that row back.
     *
     * @return the rows, unmodifiable
     */
    public static List<PathMapping> mappings() {
        return MAPPINGS;
    }

    /**
     * The row of {@link #mappings} a classified file belongs to.
     *
     * @param kind      the kind of the file
     * @param part      the part of a skill directory for a file of a skill other than its SKILL.md; null otherwise
     * @param placement the placement of an agents file; null otherwise
     * @param link      the name of a tool link; null otherwise
     * @return the row
     * @throws IllegalArgumentException when no row has that kind, part, placement and link
     */
    public static PathMapping mappingOf(FileKind kind, SkillPartKind part, AgentsFilePlacement placement, ToolLinkName link) {
        return MAPPINGS.stream()
                .filter(mapping -> mapping.kind() == kind && mapping.part() == part && mapping.placement() == placement
                        && mapping.link() == link)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(kind + " " + part + " " + placement + " " + link));
    }

    private static List<PathMapping> buildMappings() {
        List<FileFormat> markdown = List.of(FileFormat.MARKDOWN);
        List<FileFormat> any = List.of(FileFormat.ANY);
        List<FileFormat> json = List.of(FileFormat.JSON);
        String markdownFile = RenderConstant.PATTERN_PATH + RenderConstant.MARKDOWN_SUFFIX;
        String packFolder = RenderConstant.PATTERN_PACK + RenderConstant.PATH_SEPARATOR;
        String packPrefix = RenderConstant.PATTERN_PACK + RenderConstant.MOUNT_NAME_SEPARATOR;
        String mountedMarkdownFile = packFolder + markdownFile;
        String skillDirectory = skillDirectory(RenderConstant.PATTERN_NAME);
        String mountedSkillDirectory = skillDirectory(packPrefix + RenderConstant.PATTERN_NAME);
        String mountAgentsFile = mountAgentsFile(RenderConstant.PATTERN_ROOT_PATH);
        return List.of(
                agentsMapping(AgentsFilePlacement.ROOT, RenderConstant.AGENTS_FILE, mountAgentsFile, null),
                agentsMapping(AgentsFilePlacement.AGENTS_DIR, RenderConstant.AGENTS_DIRECTORY_FILE, null, null),
                agentsMapping(AgentsFilePlacement.MOUNT_ROOT, mountAgentsFile, mountAgentsFile, RenderConstant.NOTE_MOUNT_AGENTS_FILE),
                kindMapping(FileKind.RULE, RenderConstant.RULES_FOLDER + markdownFile, RenderConstant.RULES_FOLDER + mountedMarkdownFile,
                        markdown, false, null),
                kindMapping(FileKind.GUARDRAIL, RenderConstant.GUARDRAILS_FOLDER + RenderConstant.PATTERN_NAME
                        + RenderConstant.MARKDOWN_SUFFIX, null, markdown, true, RenderConstant.NOTE_GUARDRAIL),
                kindMapping(FileKind.CONVENTION, RenderConstant.CONVENTIONS_FOLDER + markdownFile,
                        RenderConstant.MOUNT_CONVENTIONS_FOLDER + mountedMarkdownFile, markdown, false, null),
                kindMapping(FileKind.ROLE, RenderConstant.ROLES_FOLDER + markdownFile, RenderConstant.ROLES_FOLDER + mountedMarkdownFile,
                        markdown, false, null),
                kindMapping(FileKind.WORKFLOW, RenderConstant.WORKFLOWS_FOLDER + markdownFile,
                        RenderConstant.WORKFLOWS_FOLDER + mountedMarkdownFile, markdown, false, null),
                kindMapping(FileKind.TEMPLATE, RenderConstant.TEMPLATES_FOLDER + markdownFile,
                        RenderConstant.TEMPLATES_FOLDER + mountedMarkdownFile, markdown, false, null),
                kindMapping(FileKind.HOOK, RenderConstant.HOOKS_FOLDER + RenderConstant.PATTERN_PATH, null, any, true, null),
                kindMapping(FileKind.SUBAGENT, RenderConstant.SUBAGENTS_FOLDER + markdownFile,
                        RenderConstant.SUBAGENTS_FOLDER + packPrefix + RenderConstant.PATTERN_NAME + RenderConstant.MARKDOWN_SUFFIX,
                        markdown, false, null),
                kindMapping(FileKind.SETTINGS, RenderConstant.SETTINGS_FILE, null, json, true, null),
                kindMapping(FileKind.SKILL, skillDirectory + RenderConstant.SKILL_FILE, mountedSkillDirectory + RenderConstant.SKILL_FILE,
                        markdown, false, null),
                partMapping(SkillPartKind.WORKFLOW, RenderConstant.SKILL_WORKFLOWS_FOLDER + RenderConstant.PATTERN_PATH,
                        markdown, false, null),
                partMapping(SkillPartKind.SCRIPT, RenderConstant.SKILL_SCRIPTS_FOLDER + RenderConstant.PATTERN_PATH,
                        any, true, RenderConstant.NOTE_SKILL_SCRIPT),
                partMapping(SkillPartKind.CONVENTION, RenderConstant.PATTERN_SKILL_CONVENTION_FOLDERS + RenderConstant.PATTERN_PATH,
                        markdown, false, null),
                partMapping(SkillPartKind.TOOL_PARAMETER, RenderConstant.PATTERN_PATH + RenderConstant.PATTERN_TOOL_PARAMETER_SUFFIXES,
                        List.of(FileFormat.JSON, FileFormat.YAML), false, RenderConstant.NOTE_SKILL_TOOL_PARAMETER),
                partMapping(SkillPartKind.REFERENCE, RenderConstant.PATTERN_PATH, any, false, RenderConstant.NOTE_SKILL_REFERENCE),
                kindMapping(FileKind.SKILL_LINK, skillLink(RenderConstant.PATTERN_NAME), skillLink(packPrefix + RenderConstant.PATTERN_NAME),
                        List.of(FileFormat.SYMLINK), false, RenderConstant.NOTE_SKILL_LINK),
                toolLinkMapping(ToolLinkName.ROOT_AGENTS_FILE),
                toolLinkMapping(ToolLinkName.CLAUDE_RULES),
                toolLinkMapping(ToolLinkName.CLAUDE_TEMPLATES)
        );
    }

    // A stored root AGENTS.md of a mounted pack is delivered in the mount folder, so ROOT and MOUNT_ROOT
    // share the mounted pattern; .agents/AGENTS.md has no place in a folder.
    private static PathMapping agentsMapping(AgentsFilePlacement placement, String pathPattern, String mountedPattern, String note) {
        return new PathMapping(FileKind.AGENTS_FILE, null, placement, null, pathPattern, mountedPattern, List.of(FileFormat.MARKDOWN),
                false, note);
    }

    // A mounted pack delivers no tool link: the links lead every tool to the root's sources.
    private static PathMapping toolLinkMapping(ToolLinkName link) {
        return new PathMapping(FileKind.TOOL_LINK, null, null, link, toolLinkPath(link), null, List.of(FileFormat.SYMLINK), false,
                String.format(RenderConstant.NOTE_TOOL_LINK, toolLinkTarget(link)));
    }

    private static PathMapping kindMapping(FileKind kind, String pathPattern, String mountedPattern, List<FileFormat> formats,
                                           boolean executable, String note) {
        return new PathMapping(kind, null, null, null, pathPattern, mountedPattern, formats, executable, note);
    }

    /**
     * A row for a part of a skill directory, at the same place below the skill directory whether the
     * pack is used at the root or mounted in a folder.
     *
     * @param inSkill the path below the skill directory
     */
    private static PathMapping partMapping(SkillPartKind part, String inSkill, List<FileFormat> formats, boolean executable,
                                           String note) {
        return new PathMapping(FileKind.SKILL, part, null, null, skillDirectory(RenderConstant.PATTERN_NAME) + inSkill,
                skillDirectory(RenderConstant.PATTERN_PACK + RenderConstant.MOUNT_NAME_SEPARATOR + RenderConstant.PATTERN_NAME)
                        + inSkill, formats, executable, note);
    }

    private static String mountAgentsFile(String mountRootPath) {
        return mountRootPath + RenderConstant.PATH_SEPARATOR + RenderConstant.AGENTS_FILE;
    }
}
