package com.korur.oba.apps.kaya.release.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaya.release.constant.ReleaseConstant;
import com.korur.oba.apps.kaya.render.helper.Frontmatter;
import com.korur.oba.apps.kaya.render.helper.FrontmatterParser;

/**
 * Reads and rewrites the paths frontmatter of a rule, for an inherited rule a mounted pack
 * overrides: the patterns under the pack's mount folder are removed so that, for files there,
 * Claude Code loads the mounted pack's rule only. Only the paths key is rewritten, as one inline
 * list; every other byte of the rule stays, so the narrowed copy reads the same everywhere outside
 * the mount folder.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RuleNarrower {

    private static final String DELIMITER = "---";
    private static final String BYTE_ORDER_MARK = "﻿";
    private static final Pattern LINE = Pattern.compile("[^\\r\\n]*(\\r\\n|\\n|\\r)?");
    private static final Pattern PATHS_KEY = Pattern.compile("^(?:paths|\"paths\"|'paths')[ \\t]*:(.*)$");
    private static final String NEWLINE = "\n";
    private static final String FLOW_OPEN = "[";
    private static final String FLOW_CLOSE = "]";
    private static final String COMMENT = "#";
    private static final char GROUP_OPEN = '{';
    private static final char GROUP_CLOSE = '}';
    private static final char SEPARATOR = ',';

    private RuleNarrower() {
    }

    /**
     * The patterns of a rule's paths key in the three forms Claude Code reads: an inline list, a
     * block list, or one string of comma-separated patterns. A comma inside a brace group belongs
     * to the group, so "a/{b,c}/**, d/**" is two patterns.
     *
     * @param frontmatter the rule's frontmatter
     * @return the patterns, trimmed; empty when the key is absent or empty
     */
    public static List<String> patterns(Frontmatter frontmatter) {
        Object value = frontmatter.values().get(ReleaseConstant.FRONTMATTER_PATHS);
        List<String> patterns = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            collection.stream()
                    .filter(Objects::nonNull)
                    .map(item -> String.valueOf(item).strip())
                    .filter(item -> !item.isEmpty())
                    .forEach(patterns::add);
        } else if (value != null) {
            patterns.addAll(splitTopLevelCommas(value.toString()));
        }
        return patterns;
    }

    /**
     * Whether the rule's paths key can be rewritten: it is a top-level key of the frontmatter
     * block, and a rewrite to the patterns it has leaves every key the parser reads unchanged. A
     * rule written in a YAML form the rewrite does not know is refused as an override target
     * rather than delivered with frontmatter that means something else.
     */
    public static boolean isNarrowable(byte[] rule) {
        String text = new String(rule, StandardCharsets.UTF_8);
        Frontmatter before = FrontmatterParser.parse(text);
        if (before.error() != null || !before.has(ReleaseConstant.FRONTMATTER_PATHS)) {
            return false;
        }
        List<String> patterns = patterns(before);
        if (patterns.isEmpty()) {
            return false;
        }
        Optional<String> rewritten = rewrite(text, patterns);
        if (rewritten.isEmpty()) {
            return false;
        }
        Frontmatter after = FrontmatterParser.parse(rewritten.get());
        Map<String, Object> otherBefore = new LinkedHashMap<>(before.values());
        Map<String, Object> otherAfter = new LinkedHashMap<>(after.values());
        otherBefore.remove(ReleaseConstant.FRONTMATTER_PATHS);
        otherAfter.remove(ReleaseConstant.FRONTMATTER_PATHS);
        return after.error() == null && patterns(after).equals(patterns) && otherAfter.equals(otherBefore);
    }

    /**
     * Rewrites a rule to keep only the given patterns.
     *
     * @param rule         the rule's bytes; {@link #isNarrowable} holds for it
     * @param keptPatterns the patterns to keep, in order
     * @return the narrowed rule, or empty when no pattern is kept and the rule is not delivered
     * @throws IllegalArgumentException when the rule's paths key cannot be rewritten
     */
    public static Optional<byte[]> narrow(byte[] rule, List<String> keptPatterns) {
        if (keptPatterns.isEmpty()) {
            return Optional.empty();
        }
        String text = new String(rule, StandardCharsets.UTF_8);
        String narrowed = rewrite(text, keptPatterns)
                .orElseThrow(() -> new IllegalArgumentException(ReleaseConstant.FRONTMATTER_PATHS));
        return Optional.of(narrowed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Replaces the lines of the paths key, its value lines included, with one inline list. The
     * line ending of the key's line is kept, so a CRLF file stays CRLF.
     */
    private static Optional<String> rewrite(String text, List<String> patterns) {
        List<int[]> lines = lines(text);
        if (lines.isEmpty() || !content(text, lines.get(0)).replace(BYTE_ORDER_MARK, "").strip().equals(DELIMITER)) {
            return Optional.empty();
        }
        for (int index = 1; index < lines.size(); index++) {
            String line = content(text, lines.get(index));
            if (line.strip().equals(DELIMITER)) {
                return Optional.empty();
            }
            Matcher key = PATHS_KEY.matcher(line);
            if (!key.matches()) {
                continue;
            }
            int last = lastValueLine(text, lines, index, key.group(1).strip());
            if (last < 0) {
                return Optional.empty();
            }
            int start = lines.get(index)[0];
            int end = lines.get(last)[1];
            String ending = ending(text, lines.get(index));
            String replacement = ReleaseConstant.FRONTMATTER_PATHS + ": " + inlineList(patterns) + ending;
            return Optional.of(text.substring(0, start) + replacement + text.substring(end));
        }
        return Optional.empty();
    }

    /**
     * The last line of the key's value: the key's own line for an inline value, the line that
     * closes a flow list spread over lines, or the last item line of a block list. Blank lines
     * between items belong to the list; blank lines after it do not.
     *
     * @return the line index, or -1 when the value does not end inside the frontmatter block
     */
    private static int lastValueLine(String text, List<int[]> lines, int keyLine, String value) {
        boolean inline = !value.isEmpty() && !value.startsWith(COMMENT);
        if (inline && value.startsWith(FLOW_OPEN) && !value.contains(FLOW_CLOSE)) {
            for (int index = keyLine + 1; index < lines.size(); index++) {
                String line = content(text, lines.get(index));
                if (line.strip().equals(DELIMITER)) {
                    return -1;
                }
                if (line.contains(FLOW_CLOSE)) {
                    return index;
                }
            }
            return -1;
        }
        if (inline) {
            return keyLine;
        }
        int last = keyLine;
        for (int index = keyLine + 1; index < lines.size(); index++) {
            String line = content(text, lines.get(index));
            if (line.strip().equals(DELIMITER)) {
                return last;
            }
            if (line.isBlank()) {
                continue;
            }
            boolean item = Character.isWhitespace(line.charAt(0)) || line.startsWith("-");
            if (!item) {
                return last;
            }
            last = index;
        }
        return -1;
    }

    private static String inlineList(List<String> patterns) {
        List<String> quoted = patterns.stream()
                .map(pattern -> "\"" + pattern.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .toList();
        return FLOW_OPEN + String.join(", ", quoted) + FLOW_CLOSE;
    }

    private static List<String> splitTopLevelCommas(String value) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == GROUP_OPEN) {
                depth++;
            } else if (character == GROUP_CLOSE) {
                depth = Math.max(0, depth - 1);
            } else if (character == SEPARATOR && depth == 0) {
                parts.add(value.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(value.substring(start));
        return parts.stream().map(String::strip).filter(part -> !part.isEmpty()).toList();
    }

    /** Each line as [start, end] offsets, the end past its line ending. */
    private static List<int[]> lines(String text) {
        List<int[]> lines = new ArrayList<>();
        Matcher matcher = LINE.matcher(text);
        int position = 0;
        while (position < text.length() && matcher.find(position)) {
            lines.add(new int[]{matcher.start(), matcher.end()});
            position = matcher.end();
        }
        return lines;
    }

    private static String content(String text, int[] line) {
        String whole = text.substring(line[0], line[1]);
        return whole.replaceAll("[\\r\\n]+$", "");
    }

    private static String ending(String text, int[] line) {
        String whole = text.substring(line[0], line[1]);
        String content = content(text, line);
        String ending = whole.substring(content.length());
        return ending.isEmpty() ? NEWLINE : ending;
    }
}
