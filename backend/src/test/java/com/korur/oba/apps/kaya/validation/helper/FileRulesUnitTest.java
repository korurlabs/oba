package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.FileCandidates;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileRulesUnitTest {

    private final FileRules rules = new FileRules();

    private static FileCandidate file(String path, String content) {
        return FileCandidates.of(path, FileMode.FILE, null, content.getBytes(StandardCharsets.UTF_8));
    }

    private static FileCandidate link(String path, String target) {
        return FileCandidates.of(path, FileMode.SYMLINK, target, null);
    }

    private List<ViolationRule> rulesOf(FileCandidate candidate) {
        return rules.validate(candidate).stream().map(Violation::rule).toList();
    }

    @Test
    void validate_validSkill_hasNoViolation() {
        String skill = "---\nname: kaizen\ndescription: Use for Kaizen tickets\nglobs: \"**/*.java\"\n---\n# Kaizen\n";
        assertEquals(List.of(), rulesOf(file(".agents/skills/kaizen/SKILL.md", skill)));
    }

    @Test
    void validate_pathOutsideTheAllowList_isPathNotAllowed() {
        assertEquals(List.of(ViolationRule.PATH_NOT_ALLOWED), rulesOf(file(".agents/memory/lessons.md", "x")));
    }

    @Test
    void validate_skillOf1500Lines_isSkillTooLong() {
        String skill = "---\nname: long\n---\n" + "line\n".repeat(1497);
        assertEquals(List.of(ViolationRule.SKILL_TOO_LONG), rulesOf(file(".agents/skills/long/SKILL.md", skill)));
        String shorter = "---\nname: long\n---\n" + "line\n".repeat(1496);
        assertEquals(List.of(), rulesOf(file(".agents/skills/long/SKILL.md", shorter)));
    }

    @Test
    void validate_unknownFrontmatterKey_isReported() {
        String skill = "---\nname: x\ncolour: blue\n---\nbody\n";
        assertEquals(List.of(ViolationRule.FRONTMATTER_KEY_UNKNOWN), rulesOf(file(".agents/skills/x/SKILL.md", skill)));
    }

    @Test
    void validate_brokenFrontmatter_isFrontmatterInvalid() {
        assertEquals(List.of(ViolationRule.FRONTMATTER_INVALID),
                rulesOf(file(".agents/skills/x/SKILL.md", "---\nname: [x\n---\nbody\n")));
        assertEquals(List.of(ViolationRule.FRONTMATTER_INVALID),
                rulesOf(file(".agents/skills/x/SKILL.md", "---\nname: x\nbody\n")));
    }

    @Test
    void validate_descriptionAndWhenToUseOver1536Characters_isTooLong() {
        String skill = "---\ndescription: " + "d".repeat(1000) + "\nwhen_to_use: " + "w".repeat(537) + "\n---\nbody\n";
        assertEquals(List.of(ViolationRule.SKILL_DESCRIPTION_TOO_LONG), rulesOf(file(".agents/skills/x/SKILL.md", skill)));
    }

    @Test
    void validate_skillFileAsSymlink_isSkillSymlink() {
        assertEquals(List.of(ViolationRule.SKILL_SYMLINK),
                rulesOf(link(".agents/skills/x/SKILL.md", "../y/SKILL.md")));
    }

    @Test
    void validate_symlinkLeavingTheRepository_isRefused() {
        assertEquals(List.of(ViolationRule.SYMLINK_OUTSIDE_REPOSITORY),
                rulesOf(link(".claude/skills/x", "../../../outside")));
        assertEquals(List.of(ViolationRule.SYMLINK_TARGET_INVALID), rulesOf(link(".claude/skills/x", "/etc/passwd")));
        assertEquals(List.of(), rulesOf(link(".claude/skills/x", "../../.agents/skills/x")));
    }

    @Test
    void validate_fileOver5MiB_isFileTooLarge() {
        FileCandidate large = FileCandidates.of(".agents/rules/big.md", FileMode.FILE, null,
                new byte[5_242_881]);
        assertEquals(List.of(ViolationRule.FILE_TOO_LARGE), rulesOf(large));
    }

    @Test
    void validate_symlinkAtTheSettings_isSymlinkContentRequired() {
        assertEquals(List.of(ViolationRule.SYMLINK_CONTENT_REQUIRED),
                rulesOf(link(".claude/settings.json", "../.agents/templates/settings.json")));
    }

    @Test
    void validate_symlinkAtAnAlwaysOnPath_isLeftToTheComposition() {
        assertEquals(List.of(), rulesOf(link("AGENTS.md", ".agents/AGENTS.md")));
        assertEquals(List.of(), rulesOf(link(".agents/rules/big.md", "../skills/big/notes.md")));
        assertEquals(List.of(), rulesOf(link(".agents/rules/scoped", "../skills/big")));
    }

    @Test
    void validate_symlinkUnderTheHooks_isHookSymlink() {
        assertEquals(List.of(ViolationRule.HOOK_SYMLINK),
                rulesOf(link(".agents/hooks/dispatch.sh", "../skills/y/dispatch.sh")));
        assertEquals(List.of(ViolationRule.HOOK_SYMLINK), rulesOf(link(".agents/hooks/lib", "../skills/y/lib")));
        assertEquals(List.of(), rulesOf(file(".agents/hooks/dispatch.sh", "#!/bin/sh\n")));
    }
}
