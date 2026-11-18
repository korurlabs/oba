package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// 4. Internal
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.validation.enums.PatternClass;

/**
 * The path rules of a pack mounted in a folder of a repository: the namespace its files must use,
 * and where a rule's paths pattern lies against the mount folder. A mounted pack's files are
 * delivered at their stored paths at the repository root, because Claude Code loads rules, skills
 * and agents from the root's .claude and .agents folders only; the namespace keeps them from
 * colliding with the files of the packs composed at the root. Only its AGENTS.md moves, to the
 * mount folder. Kept apart from {@link PathRules}, which knows nothing about mounts.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MountPaths {

    private static final String SEPARATOR = ValidationConstant.PATH_SEPARATOR;
    private static final String GLOBSTAR = "**";
    private static final String CURRENT_PREFIX = "./";
    private static final String GLOB_SYNTAX = "glob:";
    private static final Pattern GLOB_CHARACTERS = Pattern.compile("[*?\\[\\]{}]");
    private static final char GROUP_OPEN = '{';
    private static final char GROUP_CLOSE = '}';
    private static final char ALTERNATIVE = ',';

    private MountPaths() {
    }

    /** The mounted pack's AGENTS.md, the one path of its namespace below the mount folder. */
    public static String agentsFile(String root) {
        return root + SEPARATOR + ValidationConstant.AGENTS_FILE;
    }

    /**
     * Whether a path lies in a mounted pack's namespace: .agents/rules/p/, .agents/skills/p-name/ with
     * its link .claude/skills/p-name, .claude/agents/p-*.md, .agents/roles, workflows and templates
     * under p/, docs/private/conventions/projects/p/ and root/AGENTS.md.
     * Settings, hooks and the tool links are never in it, so a mounted pack can never change what
     * runs on a machine; only a pack composed at the root, which has no namespace, delivers them.
     *
     * @param path the delivered path
     * @param name the pack name
     * @param root the mount folder, relative to the repository root
     */
    public static boolean isInNamespace(String path, String name, String root) {
        if (path.equals(agentsFile(root)) || isMountRule(path, name) || isMountDoc(path, name)) {
            return true;
        }
        String prefixed = Pattern.quote(name + ValidationConstant.MOUNT_NAME_SEPARATOR);
        if (Pattern.matches(Pattern.quote(ValidationConstant.SKILLS_PREFIX) + prefixed + "[^/]+/.+", path)
                || Pattern.matches(Pattern.quote(ValidationConstant.CLAUDE_SKILLS_PREFIX) + prefixed + "[^/]+", path)
                || Pattern.matches(Pattern.quote(ValidationConstant.CLAUDE_AGENTS_PREFIX) + prefixed + "[^/]+\\.md", path)) {
            return true;
        }
        return ValidationConstant.MOUNT_DIRECTORY_PREFIXES.stream()
                .anyMatch(prefix -> path.startsWith(prefix + name + SEPARATOR) && path.length() > (prefix + name + SEPARATOR).length());
    }

    /** Whether the path is a rule file in the namespace of pack p, under .agents/rules/p/. */
    public static boolean isMountRule(String path, String name) {
        return path.startsWith(ValidationConstant.RULES_PREFIX + name + SEPARATOR) && path.endsWith(ValidationConstant.MARKDOWN_SUFFIX);
    }

    /** Whether the path is a convention doc in the namespace of pack p, under docs/private/conventions/projects/p/. */
    public static boolean isMountDoc(String path, String name) {
        return path.startsWith(ValidationConstant.MOUNT_CONVENTIONS_PREFIX + name + SEPARATOR)
                && path.endsWith(ValidationConstant.MARKDOWN_SUFFIX);
    }

    /** Whether the path is a convention doc outside every mount namespace, one a mounted pack's doc may override. */
    public static boolean isInheritedDoc(String path) {
        return path.startsWith(ValidationConstant.CONVENTIONS_PREFIX) && !path.startsWith(ValidationConstant.MOUNT_CONVENTIONS_PREFIX)
                && path.endsWith(ValidationConstant.MARKDOWN_SUFFIX);
    }

    /**
     * Classifies a paths pattern against a mount folder. Brace groups are expanded first, as Claude
     * Code expands them, and each expansion is classified on its own, so projects/{kagi,kazan}/**
     * is one INSIDE and one OUTSIDE pattern for the root projects/kagi. A pattern with more than
     * BRACE_EXPANSIONS_MAX expansions counts as one SPANNING pattern, which is refused rather than
     * expanded without bound.
     *
     * @param pattern the pattern as written in the rule
     * @param root    the mount folder
     * @return each expansion with its class, in expansion order
     */
    public static Map<String, PatternClass> classify(String pattern, String root) {
        Map<String, PatternClass> classes = new LinkedHashMap<>();
        Optional<List<String>> expansions = expandBraces(pattern);
        if (expansions.isEmpty()) {
            classes.put(pattern, PatternClass.SPANNING);
            return classes;
        }
        for (String expansion : expansions.get()) {
            classes.put(expansion, classifyExpansion(expansion, root));
        }
        return classes;
    }

    /**
     * Whether a rule of a pack mounted at root loads for files under root only: it has at least one
     * paths pattern, and every expansion of every pattern is INSIDE. A rule without paths loads at
     * every start, and an OUTSIDE or SPANNING pattern loads it for files elsewhere, so either would
     * change sessions that never touch the folder.
     *
     * @param patterns the rule's paths patterns as written; empty when it has none
     * @param root     the mount folder
     */
    public static boolean isScopedTo(List<String> patterns, String root) {
        return !patterns.isEmpty() && patterns.stream()
                .flatMap(pattern -> classify(pattern, root).values().stream())
                .allMatch(patternClass -> patternClass == PatternClass.INSIDE);
    }

    /**
     * Expands the brace groups of a pattern, outermost first and nested groups after: a{b,c}d is
     * abd and acd. A group without a comma, or a brace that is never closed, stays literal.
     *
     * @param pattern the pattern
     * @return its expansions, or empty when there are more than BRACE_EXPANSIONS_MAX
     */
    public static Optional<List<String>> expandBraces(String pattern) {
        List<String> expansions = new ArrayList<>();
        return expandInto(pattern, 0, expansions) ? Optional.of(expansions) : Optional.empty();
    }

    private static boolean expandInto(String pattern, int from, List<String> expansions) {
        int open = pattern.indexOf(GROUP_OPEN, from);
        while (open >= 0) {
            int close = matchingClose(pattern, open);
            if (close < 0) {
                break;
            }
            List<String> alternatives = alternatives(pattern.substring(open + 1, close));
            if (alternatives.size() > 1) {
                String prefix = pattern.substring(0, open);
                String suffix = pattern.substring(close + 1);
                for (String alternative : alternatives) {
                    String expanded = prefix + alternative + suffix;
                    if (!expandInto(expanded, open, expansions)) {
                        return false;
                    }
                }
                return true;
            }
            open = pattern.indexOf(GROUP_OPEN, open + 1);
        }
        expansions.add(pattern);
        return expansions.size() <= ValidationConstant.BRACE_EXPANSIONS_MAX;
    }

    private static int matchingClose(String pattern, int open) {
        int depth = 0;
        for (int index = open; index < pattern.length(); index++) {
            char character = pattern.charAt(index);
            if (character == GROUP_OPEN) {
                depth++;
            } else if (character == GROUP_CLOSE) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    /** Splits a group's body at its top-level commas; a nested group keeps its own commas. */
    private static List<String> alternatives(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < body.length(); index++) {
            char character = body.charAt(index);
            if (character == GROUP_OPEN) {
                depth++;
            } else if (character == GROUP_CLOSE) {
                depth--;
            } else if (character == ALTERNATIVE && depth == 0) {
                parts.add(body.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    /**
     * Compares an expansion with the root segment by segment: INSIDE when its first segments equal
     * the root's literally; OUTSIDE when a literal segment differs, a glob segment does not match
     * the root's segment, or the pattern ends above the root, since it then matches no file below
     * it; SPANNING when ** or a glob segment that matches the root's segment comes first.
     */
    private static PatternClass classifyExpansion(String expansion, String root) {
        String pattern = expansion.startsWith(CURRENT_PREFIX) ? expansion.substring(CURRENT_PREFIX.length()) : expansion;
        String[] segments = pattern.split(SEPARATOR, -1);
        String[] rootSegments = root.split(SEPARATOR);
        for (int index = 0; index < rootSegments.length; index++) {
            if (index >= segments.length) {
                return PatternClass.OUTSIDE;
            }
            String segment = segments[index];
            if (segment.equals(GLOBSTAR)) {
                return PatternClass.SPANNING;
            }
            if (!GLOB_CHARACTERS.matcher(segment).find()) {
                if (!segment.equals(rootSegments[index])) {
                    return PatternClass.OUTSIDE;
                }
                continue;
            }
            return matchesSegment(segment, rootSegments[index]) ? PatternClass.SPANNING : PatternClass.OUTSIDE;
        }
        return PatternClass.INSIDE;
    }

    private static boolean matchesSegment(String glob, String segment) {
        try {
            return FileSystems.getDefault().getPathMatcher(GLOB_SYNTAX + glob).matches(Path.of(segment));
        } catch (PatternSyntaxException | InvalidPathException e) {
            // A glob Java cannot read may still match the root for Claude Code, so it counts as
            // matching: the pattern is then SPANNING, which the caller reports as a violation.
            return true;
        }
    }
}
