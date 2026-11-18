package com.korur.oba.apps.kaya.session.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal

/**
 * Reads handoff snapshots by the frontend's rules, so the server and the session page agree on a
 * handoff's path and status. A handoff follows the shared template: a title, one
 * "**Status:** PARTIAL|DONE|BLOCKED" line, then "## " sections. Only the first status line before
 * the first section counts, and lines inside a code fence are text.
 */
public final class HandoffStatusParser {

    private static final Pattern STATUS_LINE = Pattern.compile("^\\*\\*Status:\\*\\*\\s*`?(PARTIAL|DONE|BLOCKED)\\b");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");
    private static final String SECTION_PREFIX = "## ";
    private static final Pattern HANDOFFS_SEGMENT = Pattern.compile("(?:^|/)handoffs/(.+)$");

    private HandoffStatusParser() {
    }

    /**
     * The path below handoffs/ of a written file, or null when the file is not a handoff.
     *
     * @param filePath the file path as a tool event reports it, with either separator
     * @return the path below the last handoffs/ directory of the path, or null
     */
    public static String handoffPathOf(String filePath) {
        Matcher found = HANDOFFS_SEGMENT.matcher(filePath.replace('\\', '/'));
        return found.find() ? found.group(1) : null;
    }

    /**
     * The status a handoff declares.
     *
     * @param markdown the snapshot as written
     * @return PARTIAL, DONE or BLOCKED, or null when the file has no status line before its first section
     */
    public static String statusOf(String markdown) {
        if (markdown == null) {
            return null;
        }
        String fence = null;
        for (String line : markdown.replace("\r\n", "\n").split("\n", -1)) {
            Matcher fenceMatch = FENCE.matcher(line);
            boolean fenceLine = fenceMatch.find();
            if (fenceLine) {
                String marker = fenceMatch.group(1);
                if (fence == null) {
                    fence = marker;
                } else if (fence.equals(marker)) {
                    fence = null;
                }
            }
            boolean inFence = fence != null || fenceLine;
            if (inFence) {
                continue;
            }
            if (line.startsWith(SECTION_PREFIX)) {
                return null;
            }
            Matcher status = STATUS_LINE.matcher(line.strip());
            if (status.find()) {
                return status.group(1);
            }
        }
        return null;
    }
}
