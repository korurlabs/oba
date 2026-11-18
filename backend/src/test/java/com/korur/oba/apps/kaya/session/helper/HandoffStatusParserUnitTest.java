package com.korur.oba.apps.kaya.session.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HandoffStatusParserUnitTest {

    @Test
    void statusOf_statusLineBeforeFirstSection_returnsStatus() {
        assertEquals("DONE", HandoffStatusParser.statusOf("# Handoff\n\n**Status:** DONE\n\n## Scope\n"));
    }

    @Test
    void statusOf_backtickedStatus_returnsStatus() {
        assertEquals("PARTIAL", HandoffStatusParser.statusOf("# H\n**Status:** `PARTIAL`\n"));
    }

    @Test
    void statusOf_crLfLineEndings_returnsStatus() {
        assertEquals("BLOCKED", HandoffStatusParser.statusOf("# H\r\n\r\n**Status:** BLOCKED\r\n"));
    }

    @Test
    void statusOf_statusAfterFirstSection_returnsNull() {
        assertNull(HandoffStatusParser.statusOf("# H\n\n## Scope\n\n**Status:** DONE\n"));
    }

    @Test
    void statusOf_statusInsideCodeFence_isText() {
        assertNull(HandoffStatusParser.statusOf("# H\n\n```\n**Status:** DONE\n```\n"));
    }

    @Test
    void statusOf_statusInsideTildeFenceThenRealStatus_returnsTheRealOne() {
        assertEquals("PARTIAL", HandoffStatusParser.statusOf("# H\n~~~\n**Status:** DONE\n~~~\n**Status:** PARTIAL\n"));
    }

    @Test
    void statusOf_unknownStatusWord_returnsNull() {
        assertNull(HandoffStatusParser.statusOf("# H\n\n**Status:** CRITICAL_ISSUES\n"));
    }

    @Test
    void statusOf_firstStatusLineWins() {
        assertEquals("DONE", HandoffStatusParser.statusOf("# H\n**Status:** DONE\n**Status:** BLOCKED\n"));
    }

    @Test
    void statusOf_nullOrEmptyInput_returnsNull() {
        assertNull(HandoffStatusParser.statusOf(null));
        assertNull(HandoffStatusParser.statusOf(""));
    }

    @Test
    void handoffPathOf_pathBelowHandoffs_returnsTheRest() {
        assertEquals("kai-42/planning.md", HandoffStatusParser.handoffPathOf("/repo/handoffs/kai-42/planning.md"));
    }

    @Test
    void handoffPathOf_windowsSeparators_areNormalised() {
        assertEquals("kai-42/planning.md", HandoffStatusParser.handoffPathOf("C:\\repo\\handoffs\\kai-42\\planning.md"));
    }

    @Test
    void handoffPathOf_fileOutsideHandoffs_returnsNull() {
        assertNull(HandoffStatusParser.handoffPathOf("/repo/docs/planning.md"));
    }
}
