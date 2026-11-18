package com.korur.oba.apps.kaya.version.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.version.constant.VersionConstant;

/**
 * Writes the unified diff a reviewer reads, in the format of git diff: a --- and +++ header, then
 * hunks with three lines of context. A missing final newline is a change of the last line and is
 * marked as git marks it, so a reviewer sees every byte that changes. Common leading and trailing
 * lines are matched first; the rest is matched by longest common subsequence, and above
 * DIFF_MAX_CELLS line pairs the rest is shown as removed and added whole, so one large file cannot
 * hold a request for long.
 */
public final class UnifiedDiff {

    private UnifiedDiff() {
    }

    /** One line, with whether it is the last line of a file that has no final newline. */
    private record Line(String text, boolean unterminated) {
    }

    /** One step of the edit script; oldBefore and newBefore count the lines of each side before it. */
    private record Edit(char kind, Line line, int oldBefore, int newBefore) {
    }

    /**
     * The unified diff of one path.
     *
     * @param path    the path, used in both headers
     * @param oldText the text before; null when the path was added
     * @param newText the text after; null when the path was deleted
     * @return the diff, every line ending in a newline; empty when the texts are equal
     */
    public static String diff(String path, String oldText, String newText) {
        List<Line> oldLines = lines(oldText);
        List<Line> newLines = lines(newText);
        if (oldLines.equals(newLines)) {
            return "";
        }
        List<Edit> edits = edits(oldLines, newLines);
        StringBuilder diff = new StringBuilder();
        diff.append(oldText == null ? VersionConstant.DIFF_OLD_NONE : VersionConstant.DIFF_OLD_PREFIX + path)
                .append(VersionConstant.LINE_BREAK);
        diff.append(newText == null ? VersionConstant.DIFF_NEW_NONE : VersionConstant.DIFF_NEW_PREFIX + path)
                .append(VersionConstant.LINE_BREAK);
        int context = VersionConstant.DIFF_CONTEXT_LINES;
        int index = 0;
        while (index < edits.size()) {
            if (edits.get(index).kind() == VersionConstant.DIFF_CONTEXT) {
                index++;
                continue;
            }
            int start = Math.max(index - context, 0);
            int lastChange = index;
            int cursor = index;
            // Changes closer than two contexts apart share one hunk, as in git.
            while (cursor < edits.size()) {
                if (edits.get(cursor).kind() != VersionConstant.DIFF_CONTEXT) {
                    lastChange = cursor;
                } else if (cursor - lastChange > 2 * context) {
                    break;
                }
                cursor++;
            }
            int end = Math.min(lastChange + context + 1, edits.size());
            appendHunk(diff, edits.subList(start, end));
            index = end;
        }
        return diff.toString();
    }

    private static void appendHunk(StringBuilder diff, List<Edit> hunk) {
        int oldCount = 0;
        int newCount = 0;
        for (Edit edit : hunk) {
            if (edit.kind() != VersionConstant.DIFF_ADDED) {
                oldCount++;
            }
            if (edit.kind() != VersionConstant.DIFF_REMOVED) {
                newCount++;
            }
        }
        Edit first = hunk.get(0);
        // A side with no lines in the hunk names the line before it, as git does (-0,0 for a new file).
        int oldStart = oldCount == 0 ? first.oldBefore() : first.oldBefore() + 1;
        int newStart = newCount == 0 ? first.newBefore() : first.newBefore() + 1;
        diff.append(String.format(VersionConstant.DIFF_HUNK_HEADER, oldStart, oldCount, newStart, newCount))
                .append(VersionConstant.LINE_BREAK);
        for (Edit edit : hunk) {
            diff.append(edit.kind()).append(edit.line().text()).append(VersionConstant.LINE_BREAK);
            if (edit.line().unterminated()) {
                diff.append(VersionConstant.DIFF_NO_NEWLINE).append(VersionConstant.LINE_BREAK);
            }
        }
    }

    private static List<Line> lines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String[] parts = text.split(VersionConstant.LINE_BREAK, -1);
        boolean terminated = text.endsWith(VersionConstant.LINE_BREAK);
        int count = terminated ? parts.length - 1 : parts.length;
        List<Line> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add(new Line(parts[index], !terminated && index == count - 1));
        }
        return lines;
    }

    private static List<Edit> edits(List<Line> oldLines, List<Line> newLines) {
        int prefix = 0;
        while (prefix < oldLines.size() && prefix < newLines.size() && oldLines.get(prefix).equals(newLines.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < oldLines.size() - prefix && suffix < newLines.size() - prefix
                && oldLines.get(oldLines.size() - 1 - suffix).equals(newLines.get(newLines.size() - 1 - suffix))) {
            suffix++;
        }
        List<Line> oldMiddle = oldLines.subList(prefix, oldLines.size() - suffix);
        List<Line> newMiddle = newLines.subList(prefix, newLines.size() - suffix);

        List<Character> kinds = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        for (int index = 0; index < prefix; index++) {
            kinds.add(VersionConstant.DIFF_CONTEXT);
            lines.add(oldLines.get(index));
        }
        middle(oldMiddle, newMiddle, kinds, lines);
        for (int index = oldLines.size() - suffix; index < oldLines.size(); index++) {
            kinds.add(VersionConstant.DIFF_CONTEXT);
            lines.add(oldLines.get(index));
        }

        List<Edit> edits = new ArrayList<>(kinds.size());
        int oldBefore = 0;
        int newBefore = 0;
        for (int index = 0; index < kinds.size(); index++) {
            char kind = kinds.get(index);
            edits.add(new Edit(kind, lines.get(index), oldBefore, newBefore));
            if (kind != VersionConstant.DIFF_ADDED) {
                oldBefore++;
            }
            if (kind != VersionConstant.DIFF_REMOVED) {
                newBefore++;
            }
        }
        return edits;
    }

    /** Appends the edits of the changed middle: removals before additions within each change. */
    private static void middle(List<Line> oldLines, List<Line> newLines, List<Character> kinds, List<Line> lines) {
        int rows = oldLines.size();
        int columns = newLines.size();
        if ((long) rows * columns > VersionConstant.DIFF_MAX_CELLS) {
            for (Line line : oldLines) {
                kinds.add(VersionConstant.DIFF_REMOVED);
                lines.add(line);
            }
            for (Line line : newLines) {
                kinds.add(VersionConstant.DIFF_ADDED);
                lines.add(line);
            }
            return;
        }
        // common[i][j]: length of the longest common subsequence of oldLines[i..] and newLines[j..].
        int[][] common = new int[rows + 1][columns + 1];
        for (int row = rows - 1; row >= 0; row--) {
            for (int column = columns - 1; column >= 0; column--) {
                common[row][column] = oldLines.get(row).equals(newLines.get(column))
                        ? common[row + 1][column + 1] + 1
                        : Math.max(common[row + 1][column], common[row][column + 1]);
            }
        }
        List<Character> pendingKinds = new ArrayList<>();
        List<Line> pendingLines = new ArrayList<>();
        int row = 0;
        int column = 0;
        while (row < rows || column < columns) {
            if (row < rows && column < columns && oldLines.get(row).equals(newLines.get(column))) {
                flush(pendingKinds, pendingLines, kinds, lines);
                kinds.add(VersionConstant.DIFF_CONTEXT);
                lines.add(oldLines.get(row));
                row++;
                column++;
            } else if (column == columns || (row < rows && common[row + 1][column] >= common[row][column + 1])) {
                pendingKinds.add(VersionConstant.DIFF_REMOVED);
                pendingLines.add(oldLines.get(row));
                row++;
            } else {
                pendingKinds.add(VersionConstant.DIFF_ADDED);
                pendingLines.add(newLines.get(column));
                column++;
            }
        }
        flush(pendingKinds, pendingLines, kinds, lines);
    }

    /** Writes a run of changes with its removals first, the order a reader expects. */
    private static void flush(List<Character> pendingKinds, List<Line> pendingLines, List<Character> kinds, List<Line> lines) {
        for (char kind : List.of(VersionConstant.DIFF_REMOVED, VersionConstant.DIFF_ADDED)) {
            for (int index = 0; index < pendingKinds.size(); index++) {
                if (pendingKinds.get(index) == kind) {
                    kinds.add(kind);
                    lines.add(pendingLines.get(index));
                }
            }
        }
        pendingKinds.clear();
        pendingLines.clear();
    }
}
