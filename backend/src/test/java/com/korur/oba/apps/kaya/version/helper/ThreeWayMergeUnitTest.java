package com.korur.oba.apps.kaya.version.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.util.Map;
import java.util.Set;

// 4. Internal
import com.korur.oba.apps.kaya.version.enums.FileChange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThreeWayMergeUnitTest {

    @Test
    void merge_onlyCurrentChanged_takesCurrent() {
        ThreeWayMerge.Outcome outcome = ThreeWayMerge.merge(
                Map.of("a", "1", "b", "1"), Map.of("a", "1", "b", "1"), Map.of("a", "2", "c", "1"));

        assertEquals(Set.of("a", "b", "c"), outcome.takeCurrent());
        assertEquals(Set.of(), outcome.conflicts());
    }

    @Test
    void merge_onlyDraftChanged_keepsDraft() {
        ThreeWayMerge.Outcome outcome = ThreeWayMerge.merge(
                Map.of("a", "1"), Map.of("a", "2", "n", "1"), Map.of("a", "1"));

        assertEquals(Set.of(), outcome.takeCurrent());
        assertEquals(Set.of(), outcome.conflicts());
    }

    @Test
    void merge_bothChangedTheSameWay_isNoConflict() {
        ThreeWayMerge.Outcome outcome = ThreeWayMerge.merge(
                Map.of("a", "1"), Map.of("a", "2"), Map.of("a", "2"));

        assertEquals(Set.of(), outcome.takeCurrent());
        assertEquals(Set.of(), outcome.conflicts());
    }

    @Test
    void merge_bothChangedDifferently_conflicts() {
        ThreeWayMerge.Outcome outcome = ThreeWayMerge.merge(
                Map.of("a", "1", "d", "1"), Map.of("a", "2", "n", "x"), Map.of("a", "3", "d", "2", "n", "y"));

        assertEquals(Set.of("a", "d", "n"), outcome.conflicts());
        assertEquals(Set.of(), outcome.takeCurrent());
    }

    @Test
    void change_eachKind_isNamed() {
        assertNull(ThreeWayMerge.change("1", "1"));
        assertEquals(FileChange.ADDED, ThreeWayMerge.change(null, "1"));
        assertEquals(FileChange.DELETED, ThreeWayMerge.change("1", null));
        assertEquals(FileChange.MODIFIED, ThreeWayMerge.change("1", "2"));
    }
}
