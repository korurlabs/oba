package com.korur.oba.apps.kaya.version.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.settings.helper.SettingsParser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutableChangeDetectorUnitTest {

    private final ExecutableChangeDetector detector =
            new ExecutableChangeDetector(new SettingsParser(new ObjectMapper()));

    private static RenderedFile file(String path, String sha256) {
        return new RenderedFile(path, FileMode.FILE, null, null, sha256, null, null);
    }

    private static RenderedFile link(String path, String target) {
        return new RenderedFile(path, FileMode.SYMLINK, target, null, null, null, null);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void markdownChange_isNotExecutable() {
        assertFalse(detector.isExecutableChange(List.of(file(".agents/rules/core.md", "a")),
                List.of(file(".agents/rules/core.md", "b"), file(".claude/skills/p/SKILL.md", "c")), null, null));
    }

    @Test
    void guardrailAddedChangedOrRemoved_isExecutable() {
        String path = ".agents/rules/guardrails/english.md";
        assertTrue(detector.isExecutableChange(List.of(), List.of(file(path, "a")), null, null));
        assertTrue(detector.isExecutableChange(List.of(file(path, "a")), List.of(file(path, "b")), null, null));
        assertTrue(detector.isExecutableChange(List.of(file(path, "a")), List.of(), null, null));
        assertFalse(detector.isExecutableChange(List.of(file(path, "a")), List.of(file(path, "a")), null, null));
    }

    @Test
    void hookScriptChange_isExecutable() {
        assertTrue(detector.isExecutableChange(List.of(), List.of(file(".agents/hooks/dispatch.sh", "a")), null, null));
        assertTrue(detector.isExecutableChange(List.of(file(".agents/hooks/x.md", "a")), List.of(), null, null));
    }

    @Test
    void executableModeChange_isExecutable() {
        RenderedFile executable = new RenderedFile(".agents/skills/x/run.sh", FileMode.EXECUTABLE, null, null, "a", null, null);
        assertTrue(detector.isExecutableChange(List.of(file(".agents/skills/x/run.sh", "a")), List.of(executable), null, null));
        assertFalse(detector.isExecutableChange(List.of(executable), List.of(executable), null, null));
    }

    @Test
    void pluginCodeUnderClaudeSkills_isExecutable() {
        assertTrue(detector.isExecutableChange(List.of(), List.of(file(".claude/skills/p/index.js", "a")), null, null));
    }

    @Test
    void settingsChange_isExecutableOnlyForHooksOrPermissions() {
        byte[] before = bytes("{\"model\":\"opus\",\"permissions\":{\"allow\":[\"Bash(ls)\"]}}");
        byte[] modelOnly = bytes("{\"model\":\"sonnet\",\"permissions\":{\"allow\":[\"Bash(ls)\"]}}");
        byte[] permission = bytes("{\"model\":\"opus\",\"permissions\":{\"allow\":[\"Bash(ls)\",\"Bash(rm:*)\"]}}");
        List<RenderedFile> old = List.of(file(".claude/settings.json", "a"));
        List<RenderedFile> next = List.of(file(".claude/settings.json", "b"));
        assertFalse(detector.isExecutableChange(old, next, before, modelOnly));
        assertTrue(detector.isExecutableChange(old, next, before, permission));
        assertTrue(detector.isExecutableChange(List.of(), next, null, permission));
    }

    @Test
    void linkUnderClaudeSkillsRetargeted_isExecutable() {
        RenderedFile before = link(".claude/skills/p", "../../.agents/skills/p");
        RenderedFile after = link(".claude/skills/p", "../../.agents/skills/q");
        assertTrue(detector.isExecutableChange(List.of(before), List.of(after), null, null));
        assertTrue(detector.isExecutableChange(List.of(), List.of(link(".claude/skills/p/run.md", "../../../.agents/skills/p/run.sh")),
                null, null));
        assertFalse(detector.isExecutableChange(List.of(before), List.of(before), null, null));
    }

    @Test
    void linkElsewhereToHookCode_isExecutable() {
        assertTrue(detector.isExecutableChange(List.of(),
                List.of(link(".agents/skills/x/run", "../../hooks/dispatch.sh")), null, null));
        List<RenderedFile> chain = List.of(link(".agents/skills/x/run", "alias"),
                link(".agents/skills/x/alias", "../../hooks/dispatch.sh"));
        assertTrue(detector.isExecutableChange(List.of(), chain, null, null));
    }

    @Test
    void linkElsewhereToDocumentation_isNotExecutable() {
        assertFalse(detector.isExecutableChange(List.of(),
                List.of(link(".agents/skills/x/guide.md", "../kazan/SKILL.md")), null, null));
    }

    @Test
    void scriptExposedByAPluginLink_isExecutable() {
        RenderedFile mirror = link(".claude/skills/x", "../../.agents/skills/x");
        List<RenderedFile> before = List.of(mirror, file(".agents/skills/x/run.sh", "a"));
        List<RenderedFile> after = List.of(mirror, file(".agents/skills/x/run.sh", "b"));
        assertTrue(detector.isExecutableChange(before, after, null, null));
        assertTrue(detector.isExecutableChange(List.of(mirror), List.of(mirror, file(".agents/skills/x/lib/index.js", "a")),
                null, null));
    }

    @Test
    void scriptExposedThroughALinkChain_isExecutable() {
        RenderedFile alias = link(".agents/skills/alias", "x");
        RenderedFile mirror = link(".claude/skills/x", "../../.agents/skills/alias");
        assertTrue(detector.isExecutableChange(List.of(alias, mirror),
                List.of(alias, mirror, file(".agents/skills/x/run.sh", "a")), null, null));
    }

    @Test
    void markdownExposedByAPluginLink_isNotExecutable() {
        RenderedFile mirror = link(".claude/skills/x", "../../.agents/skills/x");
        List<RenderedFile> before = List.of(mirror, file(".agents/skills/x/SKILL.md", "a"));
        List<RenderedFile> after = List.of(mirror, file(".agents/skills/x/SKILL.md", "b"),
                file(".agents/skills/x/reference.md", "c"));
        assertFalse(detector.isExecutableChange(before, after, null, null));
    }

    @Test
    void scriptWithoutAPluginLink_isNotExecutable() {
        RenderedFile mirror = link(".claude/skills/x", "../../.agents/skills/x");
        assertFalse(detector.isExecutableChange(List.of(file(".agents/skills/y/run.sh", "a")),
                List.of(file(".agents/skills/y/run.sh", "b")), null, null));
        assertFalse(detector.isExecutableChange(List.of(mirror, file(".agents/skills/xy/run.sh", "a")),
                List.of(mirror, file(".agents/skills/xy/run.sh", "b")), null, null));
    }
}
