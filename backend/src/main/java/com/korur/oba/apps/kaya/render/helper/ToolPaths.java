package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.PathMapping;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolGap;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolPath;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolPathCount;
import com.korur.oba.apps.kaya.render.enums.AgentTool;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.render.enums.ToolLoading;
import com.korur.oba.apps.kaya.render.enums.ToolPathState;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

/**
 * Which paths each {@link AgentTool} reads, when it loads them, the link it passes on the way and the
 * {@link DeliveryPaths#mappings} row of the source it ends at. The table describes the tools as their
 * documentation and the pack layout state them, not data Kaya stores: when a tool changes what it
 * reads, this class is the one place to change.
 *
 * It sits beside {@link DeliveryPaths} and takes every source and link from its rows through
 * {@link DeliveryPaths#mappingOf}, so the tool view cannot name a path Kaya does not deliver, and the
 * state of each read follows from the same counts as the path mapping page.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ToolPaths {

    private static final Map<AgentTool, List<ToolPath>> PATHS = buildPaths();
    private static final Map<AgentTool, List<ToolGap>> GAPS = buildGaps();

    private ToolPaths() {
    }

    /**
     * The paths one tool reads, in the order a reader follows them: what loads at start first.
     *
     * @param tool the tool
     * @return the {@link ToolPath}s, unmodifiable
     */
    public static List<ToolPath> pathsOf(AgentTool tool) {
        return PATHS.get(tool);
    }

    /**
     * What one tool reads that Kaya does not deliver.
     *
     * @param tool the tool
     * @return the {@link ToolGap}s, unmodifiable
     */
    public static List<ToolGap> notDelivered(AgentTool tool) {
        return GAPS.get(tool);
    }

    /**
     * The name a reader knows the tool by.
     *
     * @param tool the tool
     * @return its display name
     */
    public static String name(AgentTool tool) {
        return switch (tool) {
            case CLAUDE_CODE -> RenderConstant.TOOL_NAME_CLAUDE_CODE;
            case CODEX -> RenderConstant.TOOL_NAME_CODEX;
            case CURSOR -> RenderConstant.TOOL_NAME_CURSOR;
            case GEMINI_CLI -> RenderConstant.TOOL_NAME_GEMINI_CLI;
        };
    }

    /**
     * One sentence on how the tool finds the sources under .agents/.
     *
     * @param tool the tool
     * @return the summary
     */
    public static String summary(AgentTool tool) {
        return switch (tool) {
            case CLAUDE_CODE -> RenderConstant.TOOL_SUMMARY_CLAUDE_CODE;
            case CODEX -> RenderConstant.TOOL_SUMMARY_CODEX;
            case CURSOR -> RenderConstant.TOOL_SUMMARY_CURSOR;
            case GEMINI_CLI -> RenderConstant.TOOL_SUMMARY_GEMINI_CLI;
        };
    }

    /**
     * The target of a link row, relative to the link's directory, with the placeholder of its pattern.
     *
     * @param link a {@link FileKind#TOOL_LINK} or {@link FileKind#SKILL_LINK} row; null for none
     * @return the target; null when the row is null
     * @throws IllegalArgumentException when the row is not a link
     */
    public static String linkTarget(PathMapping link) {
        if (link == null) {
            return null;
        }
        if (link.kind() == FileKind.TOOL_LINK) {
            return DeliveryPaths.toolLinkTarget(link.link());
        }
        if (link.kind() == FileKind.SKILL_LINK) {
            return DeliveryPaths.skillLinkTarget(RenderConstant.PATTERN_NAME);
        }
        throw new IllegalArgumentException(link.kind() + " is not a link");
    }

    /**
     * What a scope delivers of one read, from the path mapping counts: DIRECT when a regular file
     * counts at the read path, or when the tool reads the source directly and it counts; LINKED when
     * the link and the source count; LINK_MISSING when only the source counts, so the tool cannot see
     * it; NOT_DELIVERED otherwise.
     *
     * @param path   the read
     * @param counts the count of every row in the scope; null when no version was asked for
     * @return the read with its state and count, both null without counts
     */
    public static ToolPathCount count(ToolPath path, Map<PathMapping, Integer> counts) {
        if (counts == null) {
            return new ToolPathCount(path, null, null);
        }
        int source = counts.getOrDefault(path.source(), 0);
        int direct = path.direct() == null ? 0 : counts.getOrDefault(path.direct(), 0);
        if (direct > 0) {
            return new ToolPathCount(path, ToolPathState.DIRECT, direct);
        }
        if (source == 0) {
            return new ToolPathCount(path, ToolPathState.NOT_DELIVERED, 0);
        }
        if (path.link() == null) {
            return new ToolPathCount(path, ToolPathState.DIRECT, source);
        }
        ToolPathState state = counts.getOrDefault(path.link(), 0) > 0 ? ToolPathState.LINKED : ToolPathState.LINK_MISSING;
        return new ToolPathCount(path, state, source);
    }

    private static Map<AgentTool, List<ToolPath>> buildPaths() {
        Map<AgentTool, List<ToolPath>> paths = new EnumMap<>(AgentTool.class);
        paths.put(AgentTool.CLAUDE_CODE, claudeCode());
        paths.put(AgentTool.CODEX, codex());
        paths.put(AgentTool.CURSOR, cursor());
        paths.put(AgentTool.GEMINI_CLI, geminiCli());
        return Collections.unmodifiableMap(paths);
    }

    private static List<ToolPath> claudeCode() {
        AgentTool tool = AgentTool.CLAUDE_CODE;
        String markdownFile = RenderConstant.PATH_SEPARATOR + RenderConstant.PATTERN_PATH + RenderConstant.MARKDOWN_SUFFIX;
        PathMapping skillLink = kind(FileKind.SKILL_LINK);
        return List.of(
                agentsFile(tool, null),
                new ToolPath(tool, RenderConstant.CLAUDE_RULES_LINK + markdownFile, ToolLoading.ALWAYS,
                        toolLink(ToolLinkName.CLAUDE_RULES), null, kind(FileKind.RULE), RenderConstant.TOOL_NOTE_CLAUDE_RULES),
                // A guardrail lies below .agents/rules, so Claude Code reaches it through the same link.
                new ToolPath(tool, RenderConstant.CLAUDE_RULES_LINK + RenderConstant.PATH_SEPARATOR
                        + RenderConstant.GUARDRAILS_FOLDER.substring(RenderConstant.RULES_FOLDER.length())
                        + RenderConstant.PATTERN_NAME + RenderConstant.MARKDOWN_SUFFIX, ToolLoading.ALWAYS,
                        toolLink(ToolLinkName.CLAUDE_RULES), null, kind(FileKind.GUARDRAIL), RenderConstant.TOOL_NOTE_GUARDRAIL),
                direct(tool, kind(FileKind.SETTINGS), ToolLoading.ALWAYS, null),
                new ToolPath(tool, skillLink.pathPattern() + RenderConstant.PATH_SEPARATOR + RenderConstant.SKILL_FILE,
                        ToolLoading.ON_DEMAND, skillLink, null, kind(FileKind.SKILL), RenderConstant.TOOL_NOTE_SKILL),
                direct(tool, kind(FileKind.SUBAGENT), ToolLoading.ON_DEMAND, RenderConstant.TOOL_NOTE_SUBAGENT),
                mountAgentsFile(tool, ToolLoading.ON_DEMAND, RenderConstant.TOOL_NOTE_MOUNT_AGENTS_FILE),
                direct(tool, kind(FileKind.HOOK), ToolLoading.ON_REFERENCE, RenderConstant.TOOL_NOTE_HOOK),
                new ToolPath(tool, RenderConstant.CLAUDE_TEMPLATES_LINK + markdownFile, ToolLoading.ON_REFERENCE,
                        toolLink(ToolLinkName.CLAUDE_TEMPLATES), null, kind(FileKind.TEMPLATE), RenderConstant.TOOL_NOTE_NAMED),
                direct(tool, kind(FileKind.ROLE), ToolLoading.ON_REFERENCE, RenderConstant.TOOL_NOTE_NAMED),
                direct(tool, kind(FileKind.WORKFLOW), ToolLoading.ON_REFERENCE, RenderConstant.TOOL_NOTE_NAMED),
                direct(tool, kind(FileKind.CONVENTION), ToolLoading.ON_REFERENCE, RenderConstant.TOOL_NOTE_NAMED)
        );
    }

    private static List<ToolPath> codex() {
        AgentTool tool = AgentTool.CODEX;
        List<ToolPath> paths = new ArrayList<>();
        paths.add(agentsFile(tool, null));
        paths.add(mountAgentsFile(tool, ToolLoading.ALWAYS, RenderConstant.TOOL_NOTE_MOUNT_AGENTS_FILE_MERGED));
        paths.add(direct(tool, kind(FileKind.SKILL), ToolLoading.ON_DEMAND, RenderConstant.TOOL_NOTE_SKILL));
        paths.addAll(namedByAgentsFile(tool));
        return List.copyOf(paths);
    }

    private static List<ToolPath> cursor() {
        AgentTool tool = AgentTool.CURSOR;
        List<ToolPath> paths = new ArrayList<>();
        paths.add(agentsFile(tool, null));
        paths.add(mountAgentsFile(tool, ToolLoading.ON_DEMAND, RenderConstant.TOOL_NOTE_MOUNT_AGENTS_FILE));
        paths.add(direct(tool, kind(FileKind.SKILL), ToolLoading.ON_DEMAND, RenderConstant.TOOL_NOTE_SKILL));
        paths.addAll(namedByAgentsFile(tool));
        return List.copyOf(paths);
    }

    private static List<ToolPath> geminiCli() {
        AgentTool tool = AgentTool.GEMINI_CLI;
        List<ToolPath> paths = new ArrayList<>();
        paths.add(agentsFile(tool, RenderConstant.TOOL_NOTE_GEMINI_AGENTS_FILE));
        paths.add(mountAgentsFile(tool, ToolLoading.ON_DEMAND, RenderConstant.TOOL_NOTE_MOUNT_AGENTS_FILE));
        paths.add(direct(tool, kind(FileKind.SKILL), ToolLoading.ON_DEMAND, RenderConstant.TOOL_NOTE_SKILL));
        paths.addAll(namedByAgentsFile(tool));
        return List.copyOf(paths);
    }

    // The sources under .agents/ and the convention docs, which these tools open only when AGENTS.md names
    // them. A guardrail is text to them as well; they read no Claude Code permission, so its deny rules do
    // not reach them.
    private static List<ToolPath> namedByAgentsFile(AgentTool tool) {
        return List.of(FileKind.RULE, FileKind.GUARDRAIL, FileKind.ROLE, FileKind.WORKFLOW, FileKind.TEMPLATE,
                        FileKind.CONVENTION).stream()
                .map(kind -> direct(tool, kind(kind), ToolLoading.ON_REFERENCE, kind == FileKind.GUARDRAIL
                        ? RenderConstant.TOOL_NOTE_GUARDRAIL_NAMED
                        : RenderConstant.TOOL_NOTE_NAMED_BY_AGENTS_FILE))
                .toList();
    }

    // The root AGENTS.md: the link to .agents/AGENTS.md, or a regular file Kaya delivers in its place.
    private static ToolPath agentsFile(AgentTool tool, String note) {
        return new ToolPath(tool, RenderConstant.AGENTS_FILE, ToolLoading.ALWAYS, toolLink(ToolLinkName.ROOT_AGENTS_FILE),
                agents(AgentsFilePlacement.ROOT), agents(AgentsFilePlacement.AGENTS_DIR), note);
    }

    // The AGENTS.md of a pack mounted in a folder: Codex merges it at start when it starts in or below
    // the folder, the other tools read it when they work on files below the folder.
    private static ToolPath mountAgentsFile(AgentTool tool, ToolLoading loading, String note) {
        return direct(tool, agents(AgentsFilePlacement.MOUNT_ROOT), loading, note);
    }

    private static ToolPath direct(AgentTool tool, PathMapping source, ToolLoading loading, String note) {
        return new ToolPath(tool, source.pathPattern(), loading, null, null, source, note);
    }

    private static PathMapping kind(FileKind kind) {
        return DeliveryPaths.mappingOf(kind, null, null, null);
    }

    private static PathMapping agents(AgentsFilePlacement placement) {
        return DeliveryPaths.mappingOf(FileKind.AGENTS_FILE, null, placement, null);
    }

    private static PathMapping toolLink(ToolLinkName name) {
        return DeliveryPaths.mappingOf(FileKind.TOOL_LINK, null, null, name);
    }

    private static Map<AgentTool, List<ToolGap>> buildGaps() {
        Map<AgentTool, List<ToolGap>> gaps = new EnumMap<>(AgentTool.class);
        gaps.put(AgentTool.CLAUDE_CODE, List.of(
                new ToolGap(RenderConstant.TOOL_GAP_CLAUDE_FILE, RenderConstant.TOOL_GAP_CLAUDE_FILE_NOTE),
                new ToolGap(RenderConstant.TOOL_GAP_CLAUDE_LOCAL_FILE, RenderConstant.TOOL_GAP_CLAUDE_LOCAL_FILE_NOTE),
                new ToolGap(RenderConstant.TOOL_GAP_CLAUDE_LOCAL_SETTINGS, RenderConstant.TOOL_GAP_CLAUDE_LOCAL_SETTINGS_NOTE)));
        gaps.put(AgentTool.CODEX, List.of(
                new ToolGap(RenderConstant.TOOL_GAP_CODEX_CONFIG, RenderConstant.TOOL_GAP_CODEX_CONFIG_NOTE),
                new ToolGap(RenderConstant.TOOL_GAP_CODEX_PROJECT_CONFIG, RenderConstant.TOOL_GAP_CODEX_PROJECT_CONFIG_NOTE)));
        gaps.put(AgentTool.CURSOR, List.of(
                new ToolGap(RenderConstant.TOOL_GAP_CURSOR_RULES, RenderConstant.TOOL_GAP_CURSOR_RULES_NOTE),
                new ToolGap(RenderConstant.TOOL_GAP_CURSOR_MCP, RenderConstant.TOOL_GAP_CURSOR_MCP_NOTE)));
        gaps.put(AgentTool.GEMINI_CLI, List.of(
                new ToolGap(RenderConstant.TOOL_GAP_GEMINI_FILE, RenderConstant.TOOL_GAP_GEMINI_FILE_NOTE),
                new ToolGap(RenderConstant.TOOL_GAP_GEMINI_SETTINGS, RenderConstant.TOOL_GAP_GEMINI_SETTINGS_NOTE)));
        return Collections.unmodifiableMap(gaps);
    }
}
