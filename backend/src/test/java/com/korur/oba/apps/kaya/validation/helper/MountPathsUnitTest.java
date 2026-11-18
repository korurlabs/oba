package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kaya.validation.enums.PatternClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountPathsUnitTest {

    private static final String ROOT = "projects/kagi";

    private static PatternClass classOf(String pattern) {
        Map<String, PatternClass> classes = MountPaths.classify(pattern, ROOT);
        assertEquals(1, classes.size());
        return classes.values().iterator().next();
    }

    @Test
    void isInNamespace_toolLinkOfAFolderProject_isOutside() {
        assertFalse(MountPaths.isInNamespace(".claude/rules", "kagi", ROOT));
        assertFalse(MountPaths.isInNamespace(".claude/templates", "kagi", ROOT));
        assertFalse(MountPaths.isInNamespace("AGENTS.md", "kagi", ROOT));
    }

    @Test
    void classify_literalPrefixOfTheRoot_isInside() {
        assertEquals(PatternClass.INSIDE, classOf("projects/kagi/backend/**"));
        assertEquals(PatternClass.INSIDE, classOf("./projects/kagi/**"));
    }

    @Test
    void classify_otherLiteralFolder_isOutside() {
        assertEquals(PatternClass.OUTSIDE, classOf("projects/kazan/**"));
        assertEquals(PatternClass.OUTSIDE, classOf("projects/*.md"));
    }

    @Test
    void classify_globstarOrMatchingGlobBeforeTheRoot_isSpanning() {
        assertEquals(PatternClass.SPANNING, classOf("projects/*/backend/**"));
        assertEquals(PatternClass.SPANNING, classOf("**/*.java"));
        assertEquals(PatternClass.SPANNING, classOf("projects/k*/x"));
    }

    @Test
    void classify_globThatMissesTheRootSegment_isOutside() {
        assertEquals(PatternClass.OUTSIDE, classOf("projects/z*/x"));
    }

    @Test
    void classify_braceGroup_classifiesEachExpansion() {
        Map<String, PatternClass> classes = MountPaths.classify("projects/{kagi,kazan}/x/**", ROOT);
        assertEquals(Map.of("projects/kagi/x/**", PatternClass.INSIDE, "projects/kazan/x/**", PatternClass.OUTSIDE),
                classes);
    }

    @Test
    void expandBraces_nestedAndLiteralGroups_expandsOnlyAlternatives() {
        assertEquals(Optional.of(List.of("a/b", "a/c", "a/d")), MountPaths.expandBraces("a/{b,{c,d}}"));
        assertEquals(Optional.of(List.of("a/{b}/c")), MountPaths.expandBraces("a/{b}/c"));
    }

    @Test
    void classify_tooManyExpansions_isOneSpanningPattern() {
        String pattern = "{a,b}/{a,b}/{a,b}/{a,b}/{a,b}/{a,b}/{a,b}";
        assertEquals(Optional.empty(), MountPaths.expandBraces(pattern));
        assertEquals(Map.of(pattern, PatternClass.SPANNING), MountPaths.classify(pattern, ROOT));
    }

    @Test
    void isInNamespace_projectPaths_areAccepted() {
        assertTrue(MountPaths.isInNamespace(".agents/rules/kagi/16-kagi-api-first.md", "kagi", ROOT));
        assertTrue(MountPaths.isInNamespace(".agents/skills/kagi-release/SKILL.md", "kagi", ROOT));
        assertTrue(MountPaths.isInNamespace(".claude/skills/kagi-release", "kagi", ROOT));
        assertTrue(MountPaths.isInNamespace(".claude/agents/kagi-reviewer.md", "kagi", ROOT));
        assertTrue(MountPaths.isInNamespace(".agents/roles/kagi/engineer.md", "kagi", ROOT));
        assertTrue(MountPaths.isInNamespace("docs/private/conventions/projects/kagi/backend/32-soft-delete.md", "kagi", ROOT));
        assertTrue(MountPaths.isInNamespace("projects/kagi/AGENTS.md", "kagi", ROOT));
    }

    @Test
    void isInNamespace_settingsMcpFileHooksRootAndOtherProjects_areRefused() {
        assertFalse(MountPaths.isInNamespace(".claude/settings.json", "kagi", ROOT));
        assertFalse(MountPaths.isInNamespace(".mcp.json", "kagi", ROOT));
        assertFalse(MountPaths.isInNamespace(".agents/hooks/dispatch.sh", "kagi", ROOT));
        assertFalse(MountPaths.isInNamespace("AGENTS.md", "kagi", ROOT));
        assertFalse(MountPaths.isInNamespace(".agents/rules/core.md", "kagi", ROOT));
        assertFalse(MountPaths.isInNamespace(".agents/rules/kazan/x.md", "kagi", ROOT));
        assertFalse(MountPaths.isInNamespace(".agents/skills/kagi/SKILL.md", "kagi", ROOT));
        assertFalse(MountPaths.isInNamespace("projects/kagi/operator/AGENTS.md", "kagi", ROOT));
    }

    @Test
    void isScopedTo_noPathsOrAPatternNotUnderTheFolder_isFalse() {
        assertFalse(MountPaths.isScopedTo(List.of(), ROOT));
        assertFalse(MountPaths.isScopedTo(List.of("**/*.java"), ROOT));
        assertFalse(MountPaths.isScopedTo(List.of("projects/kagi*/**"), ROOT));
        assertFalse(MountPaths.isScopedTo(List.of("{projects/kagi,projects/kazan}/**"), ROOT));
        assertFalse(MountPaths.isScopedTo(List.of("projects/kagi/**", "projects/kazan/**"), ROOT));
    }

    @Test
    void isScopedTo_everyPatternUnderTheFolder_isTrue() {
        assertTrue(MountPaths.isScopedTo(List.of("projects/kagi/**"), ROOT));
        assertTrue(MountPaths.isScopedTo(List.of("./projects/kagi/src/**"), ROOT));
        assertTrue(MountPaths.isScopedTo(List.of("projects/kagi/{backend,frontend}/**"), ROOT));
    }
}
