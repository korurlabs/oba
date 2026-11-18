package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 4. Internal

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathRulesUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "AGENTS.md", ".agents/AGENTS.md", ".claude/settings.json", ".agents/rules/core.md",
            ".agents/skills/kaizen/SKILL.md", ".agents/hooks/dispatch.sh", ".claude/agents/coding-engineer.md",
            ".claude/skills/plan/SKILL.md", ".claude/skills/kaizen", ".agents/templates/HANDOFF_TEMPLATE.md",
            "docs/private/conventions/a.md", "docs/private/conventions/backend/32-soft-delete-convention.md",
            "projects/kagi/AGENTS.md", "a/AGENTS.md", "a/b/c/d/AGENTS.md", "_x/y.z-w/AGENTS.md", ".claude/rules", ".claude/templates"})
    void isAllowedPath_deliveredPath_isAllowed(String path) {
        assertTrue(PathRules.isAllowedPath(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "README.md", ".agents/memory/lessons.md", ".agents/docs/x.md", ".mcp.json", "projects/kagi/.mcp.json", ".mcp.json.bak", ".claude/settings.local.json",
            ".agents/rules/../../etc/passwd", ".agents/rules//core.md", ".agents/rules/./core.md", ".agents\\rules\\core.md",
            "/AGENTS.md", ".agents/rules/", ".agents/pipelines/", ".agents/pipelines/../x.yaml",
            ".agents/pipelines/feature.yaml", ".agents/pipelines/kaya/feature.yaml", ".codex/config.toml", "docs/private/conventions/a.png",
            "docs/private/conventions/", "docs/private/conventions/.md", "docs/private/conventions/a.txt",
            "docs/private/conventions/../x.md", "docs/private/conventions//a.md", "docs/other/a.md", "docs/private/a.md",
            "a/b/c/d/e/AGENTS.md", ".x/AGENTS.md", "x/../AGENTS.md", "projects/.kagi/AGENTS.md", "projects/kagi/CLAUDE.md",
            "projects/kagi/agents.md", "-x/AGENTS.md", "projects//AGENTS.md", ".claude/rules/x.md", ".claude/rules/",
            ".claude/templates/HANDOFF.md", ".claude/rulesx", ".claude/roles", "projects/kagi/.claude/rules",
            ".claude/commands/plan.md", ".claude/commands/x.md"})
    void isAllowedPath_otherPath_isRefused(String path) {
        assertFalse(PathRules.isAllowedPath(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {".agents/rules", ".agents/templates", ".agents/skills/kaizen", ".claude/skills", "docs/private/conventions",
            "docs/private/conventions/backend"})
    void isAllowedDirectory_directoryWithDeliveredPathsBelow_isAllowed(String directory) {
        assertTrue(PathRules.isAllowedDirectory(directory));
    }

    @ParameterizedTest
    @ValueSource(strings = {".git", ".agents", ".agents/memory", ".claude", ".claude/rules", ".claude/templates", ".claude/commands", "docs", "src", ""})
    void isAllowedDirectory_otherDirectory_isRefused(String directory) {
        assertFalse(PathRules.isAllowedDirectory(directory));
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/private/conventions/a.md", "docs/private/conventions/backend/32-soft-delete-convention.md"})
    void isAlwaysOnPath_conventionDoc_isNotAlwaysOn(String path) {
        assertFalse(PathRules.isAlwaysOnPath(path));
        assertFalse(PathRules.isRuleFile(path));
    }

    @Test
    void isAllowedPath_over500Characters_isRefused() {
        assertFalse(PathRules.isAllowedPath(".agents/rules/" + "a".repeat(500) + ".md"));
    }

    @Test
    void resolveSymlink_relativeTarget_resolvesAgainstTheLinkDirectory() {
        assertEquals(Optional.of(".agents/skills/kaizen"),
                PathRules.resolveSymlink(".claude/skills/kaizen", "../../.agents/skills/kaizen"));
        assertEquals(Optional.of(".agents/rules/core.md"),
                PathRules.resolveSymlink(".agents/rules/link.md", "./core.md"));
    }

    @Test
    void resolveSymlink_targetLeavingTheRepository_isEmpty() {
        assertEquals(Optional.empty(), PathRules.resolveSymlink(".claude/skills/kaizen", "../../../outside"));
        assertEquals(Optional.empty(), PathRules.resolveSymlink("AGENTS.md", ".."));
    }

    @Test
    void isSkillFile_matchesOnlyTheSkillFileOfASkillDirectory() {
        assertTrue(PathRules.isSkillFile(".agents/skills/kaizen/SKILL.md"));
        assertTrue(PathRules.isSkillFile(".claude/skills/plugin/SKILL.md"));
        assertFalse(PathRules.isSkillFile(".agents/skills/kaizen/reference/SKILL.md"));
        assertFalse(PathRules.isSkillFile(".agents/skills/SKILL.md"));
        assertEquals(".agents/skills/kaizen", PathRules.skillDirectory(".agents/skills/kaizen/SKILL.md"));
        assertEquals("kaizen", PathRules.skillDirectoryName(".agents/skills/kaizen/SKILL.md"));
    }

    @Test
    void resolveSymlink_chainThroughAnotherLink_climbsFromWhereThatLinkPoints() {
        Map<String, String> links = Map.of(".agents/skills/s/d/a", "../../..", ".agents/skills/s/d/b", "a/../../../..");
        assertEquals(Optional.of(".agents"), PathRules.resolveSymlink(".agents/skills/s/d/b", "a/../../../.."));
        assertEquals(SymlinkResolution.of(".agents"),
                PathRules.resolveSymlink(".agents/skills/s/d/a", "../../..", links));
        assertTrue(PathRules.resolveSymlink(".agents/skills/s/d/b", "a/../../../..", links).outside());
    }

    @Test
    void resolveSymlink_linkToALink_endsAtTheLastTarget() {
        Map<String, String> links = Map.of(".claude/skills/k", "../../.agents/skills/alias", ".agents/skills/alias", "kaizen");
        assertEquals(SymlinkResolution.of(".agents/skills/kaizen"),
                PathRules.resolveSymlink(".claude/skills/k", "../../.agents/skills/alias", links));
    }

    @Test
    void resolveSymlink_loop_isReportedAsALoop() {
        Map<String, String> links = Map.of(".agents/rules/a.md", "b.md", ".agents/rules/b.md", "a.md");
        SymlinkResolution resolution = PathRules.resolveSymlink(".agents/rules/a.md", "b.md", links);
        assertTrue(resolution.loop());
        assertFalse(resolution.outside());
    }

    @Test
    void linkAbove_pathBelowADeliveredLink_namesTheLink() {
        Map<String, String> links = Map.of(".agents/skills/x", "../../.git/hooks");
        assertEquals(Optional.of(".agents/skills/x"), PathRules.linkAbove(".agents/skills/x/pre-commit", links));
        assertEquals(Optional.empty(), PathRules.linkAbove(".agents/skills/x", links));
        assertEquals(Optional.empty(), PathRules.linkAbove(".agents/skills/xy/SKILL.md", links));
    }

    @Test
    void linkAbove_ancestorInAnotherLetterCase_namesTheLink() {
        Map<String, String> links = Map.of(".agents/skills/X", "../../.git/hooks");
        assertEquals(Optional.of(".agents/skills/X"), PathRules.linkAbove(".agents/skills/x/pre-commit", links));
    }

    @Test
    void resolveSymlink_segmentInAnotherLetterCase_followsTheLink() {
        Map<String, String> links = Map.of(".agents/skills/x", "../../.git/hooks",
                ".agents/roles/a", "../skills/X/../hooks/h");
        assertEquals(SymlinkResolution.of(".git/hooks/h"),
                PathRules.resolveSymlink(".agents/roles/a", "../skills/X/../hooks/h", links));
    }

    @Test
    void caseConflicts_pathsThatDifferOnlyInCase_nameTheEarlierSpelling() {
        assertEquals(Map.of(".agents/skills/x/run.sh", ".agents/skills/X"),
                PathRules.caseConflicts(List.of(".agents/skills/X", ".agents/skills/x/run.sh")));
        assertEquals(Map.of(".agents/rules/Java.md", ".agents/rules/java.md"),
                PathRules.caseConflicts(List.of(".agents/rules/java.md", ".agents/rules/Java.md")));
        assertEquals(Map.of(".agents/skills/caf\u0065\u0301/SKILL.md", ".agents/skills/caf\u00e9"),
                PathRules.caseConflicts(List.of(".agents/skills/caf\u00e9/SKILL.md", ".agents/skills/caf\u0065\u0301/SKILL.md")));
        assertEquals(Map.of(), PathRules.caseConflicts(List.of(".agents/skills/x/SKILL.md", ".agents/skills/x/run.sh",
                ".agents/skills/xy/SKILL.md", ".agents/skills/x/SKILL.md")));
    }

    @Test
    void itemOf_fileOfASkill_isTheSkillDirectory() {
        assertEquals(".agents/skills/kaizen/", PathRules.itemOf(".agents/skills/kaizen/SKILL.md"));
        assertEquals(".agents/skills/kaizen/", PathRules.itemOf(".agents/skills/kaizen/scripts/run.sh"));
        assertEquals(".agents/rules/core.md", PathRules.itemOf(".agents/rules/core.md"));
    }

    @Test
    void isLocked_requiredAncestorOrGuardrail_isLocked() {
        assertTrue(PathRules.isLocked(".agents/rules/core.md", true));
        assertTrue(PathRules.isLocked(".agents/rules/guardrails/no-push.md", false));
        assertFalse(PathRules.isLocked(".agents/rules/core.md", false));
    }
}
