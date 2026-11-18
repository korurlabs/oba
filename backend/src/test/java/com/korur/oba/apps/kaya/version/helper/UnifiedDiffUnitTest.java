package com.korur.oba.apps.kaya.version.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK

// 4. Internal

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedDiffUnitTest {

    @Test
    void diff_equalTexts_returnsEmpty() {
        assertEquals("", UnifiedDiff.diff("a.md", "a\nb\n", "a\nb\n"));
    }

    @Test
    void diff_oneLineChanged_writesOneHunkWithContext() {
        String diff = UnifiedDiff.diff("AGENTS.md", "a\nb\nc\n", "a\nB\nc\n");

        assertEquals("--- a/AGENTS.md\n+++ b/AGENTS.md\n@@ -1,3 +1,3 @@\n a\n-b\n+B\n c\n", diff);
    }

    @Test
    void diff_addedFile_writesDevNullAndZeroStart() {
        String diff = UnifiedDiff.diff("new.md", null, "x\ny\n");

        assertEquals("--- /dev/null\n+++ b/new.md\n@@ -0,0 +1,2 @@\n+x\n+y\n", diff);
    }

    @Test
    void diff_deletedFile_writesDevNullOnTheNewSide() {
        String diff = UnifiedDiff.diff("old.md", "x\n", null);

        assertEquals("--- a/old.md\n+++ /dev/null\n@@ -1,1 +0,0 @@\n-x\n", diff);
    }

    @Test
    void diff_finalNewlineRemoved_marksTheLastLine() {
        String diff = UnifiedDiff.diff("a.md", "a\nb\n", "a\nb");

        assertEquals("--- a/a.md\n+++ b/a.md\n@@ -1,2 +1,2 @@\n a\n-b\n+b\n\\ No newline at end of file\n", diff);
    }

    @Test
    void diff_changesFarApart_writesTwoHunks() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        for (int line = 1; line <= 20; line++) {
            before.append(line).append('\n');
            after.append(line == 2 ? "two" : line == 19 ? "nineteen" : String.valueOf(line)).append('\n');
        }

        String diff = UnifiedDiff.diff("n.md", before.toString(), after.toString());

        assertTrue(diff.contains("@@ -1,5 +1,5 @@\n 1\n-2\n+two\n 3\n 4\n 5\n"), diff);
        assertTrue(diff.contains("@@ -16,5 +16,5 @@\n 16\n 17\n 18\n-19\n+nineteen\n 20\n"), diff);
    }

    @Test
    void diff_changesCloseTogether_shareOneHunk() {
        String diff = UnifiedDiff.diff("n.md", "1\n2\n3\n4\n5\n6\n7\n8\n", "1\nX\n3\n4\n5\n6\nY\n8\n");

        assertEquals(1, diff.split("@@ -").length - 1, diff);
        assertTrue(diff.contains("@@ -1,8 +1,8 @@\n"), diff);
    }

    @Test
    void diff_insertedLine_keepsTheCommonLinesAsContext() {
        String diff = UnifiedDiff.diff("a.md", "a\nc\n", "a\nb\nc\n");

        assertEquals("--- a/a.md\n+++ b/a.md\n@@ -1,2 +1,3 @@\n a\n+b\n c\n", diff);
    }
}
