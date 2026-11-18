package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;

/**
 * The path rules of delivered config. The allow-list is the same as the database check on
 * config_version_files, so a path the application accepts is never refused by the database with a
 * generic error, and the CLI applies it again before it writes a file.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PathRules {

    // .claude/rules and .claude/templates are the tool links into .agents/; they are allowed exactly and
    // nothing below them, so no file is ever written through such a link.
    private static final Pattern ALLOWED_PATH = Pattern.compile(
            "^(AGENTS\\.md|\\.agents/AGENTS\\.md|\\.claude/settings\\.json|\\.claude/(rules|templates)"
                    + "|\\.agents/(rules|skills|roles|workflows|templates|hooks)/.+|\\.claude/(agents|skills)/.+"
                    + "|docs/private/conventions/.+\\.md|([A-Za-z0-9_][A-Za-z0-9._-]*/){1,4}AGENTS\\.md)$");
    private static final Pattern SKILL_FILE = Pattern.compile("^\\.(agents|claude)/skills/([^/]+)/SKILL\\.md$");
    private static final String SEPARATOR = "/";
    private static final String PARENT = "..";
    private static final String CURRENT = ".";
    // The names a directory is probed with: any file below it, or a Markdown file for the convention docs.
    private static final String DIRECTORY_PROBE = "x";
    private static final String MARKDOWN_DIRECTORY_PROBE = "x.md";

    private PathRules() {
    }

    /**
     * Whether a path may be delivered: on the allow-list, at most 500 characters, relative, with
     * no empty, "." or ".." segment and no backslash, so it cannot leave the repository.
     */
    public static boolean isAllowedPath(String path) {
        if (path == null || path.isEmpty() || path.length() > ValidationConstant.PATH_MAX_LENGTH
                || path.contains("\\") || !ALLOWED_PATH.matcher(path).matches()) {
            return false;
        }
        for (String segment : path.split(SEPARATOR, -1)) {
            if (segment.isEmpty() || segment.equals(CURRENT) || segment.equals(PARENT)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a delivered path can lie below a directory, such as .agents/rules: a file of any name
     * there, or a Markdown file for the convention docs, is on the allow-list. A link may point at such
     * a directory, as the tool link .claude/rules points at .agents/rules. The CLI's IsAllowedDir
     * applies the same probe.
     *
     * @param directory the directory, relative to the repository root, without a trailing slash
     * @return whether a delivered path can lie below it
     */
    public static boolean isAllowedDirectory(String directory) {
        if (directory == null || directory.isEmpty()) {
            return false;
        }
        return isAllowedPath(directory + SEPARATOR + DIRECTORY_PROBE) || isAllowedPath(directory + SEPARATOR + MARKDOWN_DIRECTORY_PROBE);
    }

    /**
     * Resolves a symlink target against the link's directory, without other links.
     *
     * @param linkPath the path of the link
     * @param target   its relative target
     * @return the repository-relative path it points at, or empty when it leaves the repository
     */
    public static Optional<String> resolveSymlink(String linkPath, String target) {
        return Optional.ofNullable(resolveSymlink(linkPath, target, Map.of()).path());
    }

    /**
     * Resolves a symlink target as the file system does on the developer's machine: segment by
     * segment, and a segment that names another delivered link is replaced by that link's target
     * before the walk goes on. A ".." after a link therefore climbs from where the link points, not
     * from where it lies, which is how a chain such as a -> ../../.. and b -> a/../../../.. leaves
     * the repository although b looks lexically inside it. A segment matches a link whatever its
     * letter case, as on the case-insensitive file system of macOS.
     *
     * @param linkPath the path of the link
     * @param target   its relative target
     * @param links    the targets of the links delivered beside it, by path
     * @return where it ends; outside the repository (the root counts as outside, it is never a
     * delivered path) or a loop of more than SYMLINK_MAX_HOPS links
     */
    public static SymlinkResolution resolveSymlink(String linkPath, String target, Map<String, String> links) {
        if (target == null || target.startsWith(SEPARATOR) || target.contains("\\")) {
            return SymlinkResolution.outsideRepository();
        }
        Map<String, String> foldedLinks = foldKeys(links);
        Deque<String> resolved = new ArrayDeque<>(parentSegments(linkPath));
        Deque<String> pending = new ArrayDeque<>(List.of(target.split(SEPARATOR)));
        int hops = 0;
        while (!pending.isEmpty()) {
            String segment = pending.removeFirst();
            if (segment.isEmpty() || segment.equals(CURRENT)) {
                continue;
            }
            if (segment.equals(PARENT)) {
                if (resolved.isEmpty()) {
                    return SymlinkResolution.outsideRepository();
                }
                resolved.removeLast();
                continue;
            }
            resolved.addLast(segment);
            String next = foldedLinks.get(fold(String.join(SEPARATOR, resolved)));
            if (next == null) {
                continue;
            }
            hops++;
            if (hops > ValidationConstant.SYMLINK_MAX_HOPS) {
                return SymlinkResolution.looping();
            }
            if (next.startsWith(SEPARATOR) || next.contains("\\")) {
                return SymlinkResolution.outsideRepository();
            }
            resolved.removeLast();
            List<String> nextSegments = List.of(next.split(SEPARATOR));
            for (int index = nextSegments.size() - 1; index >= 0; index--) {
                pending.addFirst(nextSegments.get(index));
            }
        }
        return resolved.isEmpty()
                ? SymlinkResolution.outsideRepository()
                : SymlinkResolution.of(String.join(SEPARATOR, resolved));
    }

    /**
     * The nearest proper ancestor of a path that is a delivered link, such as .agents/skills/x for
     * .agents/skills/x/pre-commit when .agents/skills/x is a link. An ancestor matches a link
     * whatever its letter case, as on the case-insensitive file system of macOS.
     *
     * @param path  the delivered path
     * @param links the targets of the delivered links, by path
     * @return that link as delivered, or empty when no ancestor is a link
     */
    public static Optional<String> linkAbove(String path, Map<String, String> links) {
        Map<String, String> foldedPaths = new HashMap<>();
        for (String link : links.keySet()) {
            foldedPaths.put(fold(link), link);
        }
        List<String> parents = parentSegments(path);
        for (int length = 1; length <= parents.size(); length++) {
            String link = foldedPaths.get(fold(String.join(SEPARATOR, parents.subList(0, length))));
            if (link != null) {
                return Optional.of(link);
            }
        }
        return Optional.empty();
    }

    /**
     * The delivered paths that a case-insensitive file system, the default on macOS, would write
     * to the same place as an earlier path although the strings differ: the path or one of its
     * directories differs from an earlier one only in letter case or Unicode normalization, such
     * as .agents/skills/X delivered as a link and .agents/skills/x/run.sh as a file. The allow-list,
     * the hook and plugin prefixes and the composition's set of delivered paths compare exact
     * strings, so such a pair is refused; only a link lookup, which a free-form target can reach in
     * any spelling, matches case-folded.
     *
     * @param paths the delivered paths, in a stable order
     * @return each such path with the earlier spelling it collides with, in the order given
     */
    public static Map<String, String> caseConflicts(List<String> paths) {
        Map<String, String> spellings = new HashMap<>();
        Map<String, String> conflicts = new LinkedHashMap<>();
        for (String path : paths) {
            if (path == null) {
                continue;
            }
            List<String> segments = List.of(path.split(SEPARATOR));
            for (int length = 1; length <= segments.size(); length++) {
                String prefix = String.join(SEPARATOR, segments.subList(0, length));
                String earlier = spellings.putIfAbsent(fold(prefix), prefix);
                if (earlier != null && !earlier.equals(prefix)) {
                    conflicts.putIfAbsent(path, earlier);
                    break;
                }
            }
        }
        return conflicts;
    }

    /** A path as a case-insensitive, normalization-insensitive file system compares it. */
    private static String fold(String path) {
        return Normalizer.normalize(path, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> foldKeys(Map<String, String> links) {
        Map<String, String> folded = new HashMap<>();
        for (Map.Entry<String, String> link : links.entrySet()) {
            folded.put(fold(link.getKey()), link.getValue());
        }
        return folded;
    }

    /**
     * Whether a path must hold real content, never a symlink: .claude/settings.json, whose JSON,
     * hooks and permissions are checked and which the CLI writes as a regular file. Hook scripts are
     * never links either, by their own rule.
     */
    public static boolean requiresContent(String path) {
        return isSettingsFile(path);
    }

    /**
     * Whether a path is one Claude Code loads at every start: AGENTS.md, .agents/AGENTS.md and
     * everything under .agents/rules. Such a path may be a link only when it resolves to a
     * delivered regular file, because Claude Code must load that file and its content is checked
     * there. The convention docs are never always-on: a session reads one only when a rule
     * or skill cites it.
     */
    public static boolean isAlwaysOnPath(String path) {
        return isAgentsFile(path) || path.startsWith(ValidationConstant.RULES_PREFIX);
    }

    private static List<String> parentSegments(String path) {
        List<String> segments = List.of(path.split(SEPARATOR));
        return segments.subList(0, segments.size() - 1);
    }

    /** Whether the path is the SKILL.md of a skill (.agents/skills) or a plugin (.claude/skills). */
    public static boolean isSkillFile(String path) {
        return SKILL_FILE.matcher(path).matches();
    }

    /** The directory of a skill's SKILL.md, such as .agents/skills/kaizen. */
    public static String skillDirectory(String skillFilePath) {
        return skillFilePath.substring(0, skillFilePath.length() - ValidationConstant.SKILL_FILE_SUFFIX.length());
    }

    /** The directory name of a skill, its name when the frontmatter gives none. */
    public static String skillDirectoryName(String skillFilePath) {
        Matcher matcher = SKILL_FILE.matcher(skillFilePath);
        return matcher.matches() ? matcher.group(2) : skillFilePath;
    }

    /** Whether a SKILL.md lies under .claude/skills, where real files are a plugin. */
    public static boolean isPluginPath(String path) {
        return path.startsWith(ValidationConstant.CLAUDE_SKILLS_PREFIX);
    }

    /** Whether Claude Code loads the file at launch whatever the session does. */
    public static boolean isAgentsFile(String path) {
        return path.equals(ValidationConstant.AGENTS_FILE) || path.equals(ValidationConstant.AGENTS_DIRECTORY_FILE);
    }

    /** Whether the path is a rule file, always-on unless its frontmatter scopes it with paths. */
    public static boolean isRuleFile(String path) {
        return path.startsWith(ValidationConstant.RULES_PREFIX) && path.endsWith(ValidationConstant.MARKDOWN_SUFFIX);
    }

    /**
     * Whether the path is a rule that its paths frontmatter may scope: a rule file that is not a
     * guardrail. A guardrail lies under .agents/rules/ too, but its frontmatter forbids paths
     * because it limits every session, so no folder scope applies to it.
     */
    public static boolean isScopableRule(String path) {
        return isRuleFile(path) && !isGuardrailPath(path);
    }

    /**
     * Whether the path is a guardrail file, or lies in the guardrail folder: its text limits every
     * session and its deny rules are merged into the delivered settings.
     */
    public static boolean isGuardrailPath(String path) {
        return path.startsWith(ValidationConstant.GUARDRAILS_PREFIX);
    }

    /**
     * The item a delivered path belongs to: a skill's directory, such as .agents/skills/kaizen/, for
     * every file of the skill, since a skill is replaced whole; the path itself for every other file.
     */
    public static String itemOf(String path) {
        if (!path.startsWith(ValidationConstant.SKILLS_PREFIX)) {
            return path;
        }
        int end = path.indexOf(SEPARATOR, ValidationConstant.SKILLS_PREFIX.length());
        return end < 0 ? path : path.substring(0, end + 1);
    }

    /**
     * Whether an ancestor's item is locked for every descendant of that ancestor: every item of a
     * required pack, which sets limits for every repository, and every guardrail, which limits
     * every session.
     *
     * @param item             the item, as {@link #itemOf} gives it
     * @param ancestorRequired whether the ancestor that gives it is required
     */
    public static boolean isLocked(String item, boolean ancestorRequired) {
        return ancestorRequired || isGuardrailPath(item);
    }

    /** Whether the path holds the Claude Code settings: permissions and hook registrations. */
    public static boolean isSettingsFile(String path) {
        return path.equals(ValidationConstant.SETTINGS_PATH);
    }

    /** Whether the path is a hook script, which runs on the developer's machine. */
    public static boolean isHookPath(String path) {
        return path.startsWith(ValidationConstant.HOOKS_PREFIX);
    }
}
