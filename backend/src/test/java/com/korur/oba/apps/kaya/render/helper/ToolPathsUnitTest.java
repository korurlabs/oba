package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.dto.embedded.PathMapping;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolGap;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolPath;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolPathCount;
import com.korur.oba.apps.kaya.render.enums.AgentTool;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.render.enums.ToolLoading;
import com.korur.oba.apps.kaya.render.enums.ToolPathState;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;
import com.korur.oba.apps.kaya.validation.helper.PathRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The static tool table against the path rules: every link a tool passes resolves to the folder of
 * the source it ends at, every tool link is read by a tool, no tool reads one path twice, and the
 * state of a read follows from the counts of its link, source and regular file.
 */
class ToolPathsUnitTest {

    @Test
    void pathsOf_everyLinkedRead_resolvesThroughItsLinkToItsSourcePattern() {
        for (AgentTool tool : AgentTool.values()) {
            for (ToolPath path : ToolPaths.pathsOf(tool)) {
                if (path.link() == null) {
                    assertEquals(path.source().pathPattern(), path.readPath(), tool + " " + path.readPath());
                    continue;
                }
                String linkPath = path.link().pathPattern();
                String resolved = PathRules.resolveSymlink(linkPath, ToolPaths.linkTarget(path.link())).orElseThrow();
                assertTrue(path.readPath().startsWith(linkPath), path.readPath());
                assertEquals(path.source().pathPattern(), resolved + path.readPath().substring(linkPath.length()),
                        tool + " " + path.readPath());
            }
        }
    }

    @Test
    void pathsOf_everyToolLinkRow_isReadByAtLeastOneTool() {
        Set<PathMapping> links = new HashSet<>();
        for (AgentTool tool : AgentTool.values()) {
            ToolPaths.pathsOf(tool).stream().map(ToolPath::link).filter(link -> link != null).forEach(links::add);
        }

        for (ToolLinkName name : ToolLinkName.values()) {
            assertTrue(links.contains(DeliveryPaths.mappingOf(FileKind.TOOL_LINK, null, null, name)), name.name());
        }
    }

    @Test
    void pathsOf_eachTool_readsEveryPathOnceAndOnlyRowsOfTheMapping() {
        for (AgentTool tool : AgentTool.values()) {
            List<ToolPath> paths = ToolPaths.pathsOf(tool);
            assertFalse(paths.isEmpty(), tool.name());
            assertEquals(paths.size(), paths.stream().map(ToolPath::readPath).distinct().count(), tool.name());
            for (ToolPath path : paths) {
                assertEquals(tool, path.tool());
                assertTrue(DeliveryPaths.mappings().contains(path.source()), path.readPath());
                assertTrue(path.direct() == null || DeliveryPaths.mappings().contains(path.direct()), path.readPath());
                assertTrue(path.link() == null || path.link().kind() == FileKind.TOOL_LINK
                        || path.link().kind() == FileKind.SKILL_LINK, path.readPath());
            }
        }
    }

    @Test
    void pathsOf_claudeCode_readsTheRulesThroughTheirLinkAtStart() {
        ToolPath rules = read(AgentTool.CLAUDE_CODE, ".claude/rules/<path>.md");

        assertEquals(ToolLoading.ALWAYS, rules.loading());
        assertEquals(".claude/rules", rules.link().pathPattern());
        assertEquals("../.agents/rules", ToolPaths.linkTarget(rules.link()));
        assertEquals(".agents/rules/<path>.md", rules.source().pathPattern());
        assertEquals("../../.agents/skills/<name>", ToolPaths.linkTarget(read(AgentTool.CLAUDE_CODE,
                ".claude/skills/<name>/SKILL.md").link()));
    }

    @Test
    void pathsOf_claudeCode_readsTheGuardrailsThroughTheRulesLinkAtStart() {
        ToolPath guardrails = read(AgentTool.CLAUDE_CODE, ".claude/rules/guardrails/<name>.md");

        assertEquals(ToolLoading.ALWAYS, guardrails.loading());
        assertEquals(".claude/rules", guardrails.link().pathPattern());
        assertEquals(FileKind.GUARDRAIL, guardrails.source().kind());
        assertEquals(".agents/rules/guardrails/<name>.md", guardrails.source().pathPattern());
    }

    @Test
    void pathsOf_codexCursorAndGemini_readTheGuardrailsOnlyWhenNamed() {
        for (AgentTool tool : List.of(AgentTool.CODEX, AgentTool.CURSOR, AgentTool.GEMINI_CLI)) {
            ToolPath guardrails = read(tool, ".agents/rules/guardrails/<name>.md");
            assertEquals(ToolLoading.ON_REFERENCE, guardrails.loading(), tool.name());
            assertNull(guardrails.link(), tool.name());
            assertEquals(FileKind.GUARDRAIL, guardrails.source().kind(), tool.name());
        }
    }

    @Test
    void pathsOf_everyTool_readsTheRootAgentsFileFirstAndHasANameAndGaps() {
        for (AgentTool tool : AgentTool.values()) {
            ToolPath first = ToolPaths.pathsOf(tool).get(0);
            assertEquals("AGENTS.md", first.readPath(), tool.name());
            assertEquals(ToolLoading.ALWAYS, first.loading());
            assertEquals(agents(AgentsFilePlacement.ROOT), first.direct());
            assertEquals(agents(AgentsFilePlacement.AGENTS_DIR), first.source());
            assertFalse(ToolPaths.name(tool).isBlank());
            assertFalse(ToolPaths.summary(tool).isBlank());
            assertFalse(ToolPaths.notDelivered(tool).isEmpty(), tool.name());
        }
    }

    @Test
    void pathsOf_codexCursorAndGemini_readTheSkillsUnderAgentsDirectlyOnDemand() {
        for (AgentTool tool : List.of(AgentTool.CODEX, AgentTool.CURSOR, AgentTool.GEMINI_CLI)) {
            ToolPath skills = read(tool, ".agents/skills/<name>/SKILL.md");
            assertEquals(ToolLoading.ON_DEMAND, skills.loading(), tool.name());
            assertNull(skills.link(), tool.name());
            assertEquals(FileKind.SKILL, skills.source().kind(), tool.name());
        }
    }

    @Test
    void pathsOf_mountAgentsFile_isMergedAtStartByCodexAndReadOnDemandByTheOthers() {
        assertEquals(ToolLoading.ALWAYS, read(AgentTool.CODEX, "<rootPath>/AGENTS.md").loading());
        for (AgentTool tool : List.of(AgentTool.CLAUDE_CODE, AgentTool.CURSOR, AgentTool.GEMINI_CLI)) {
            ToolPath mounted = read(tool, "<rootPath>/AGENTS.md");
            assertEquals(ToolLoading.ON_DEMAND, mounted.loading(), tool.name());
            assertEquals(agents(AgentsFilePlacement.MOUNT_ROOT), mounted.source(), tool.name());
            assertTrue(mounted.note().startsWith("AGENTS.md of a pack mounted in a folder"), tool.name());
        }
    }

    @Test
    void notDelivered_claudeCodeAndCodex_nameTheLocalInstructionsAndTheProjectConfig() {
        assertEquals(List.of("CLAUDE.md", "CLAUDE.local.md", ".claude/settings.local.json"),
                ToolPaths.notDelivered(AgentTool.CLAUDE_CODE).stream().map(ToolGap::path).toList());
        assertEquals(List.of("~/.codex/config.toml", ".codex/config.toml"),
                ToolPaths.notDelivered(AgentTool.CODEX).stream().map(ToolGap::path).toList());
    }

    @Test
    void linkTarget_rowThatIsNoLink_throws() {
        assertNull(ToolPaths.linkTarget(null));
        assertThrows(IllegalArgumentException.class, () -> ToolPaths.linkTarget(DeliveryPaths.mappingOf(FileKind.RULE, null, null, null)));
    }

    @Test
    void count_linkAndSourceDelivered_isLinkedWithTheSourceCount() {
        ToolPath rules = read(AgentTool.CLAUDE_CODE, ".claude/rules/<path>.md");

        ToolPathCount count = ToolPaths.count(rules, counts(rules.link(), 1, rules.source(), 3));

        assertEquals(ToolPathState.LINKED, count.state());
        assertEquals(3, count.count());
    }

    @Test
    void count_sourceWithoutItsLink_isLinkMissing() {
        ToolPath rules = read(AgentTool.CLAUDE_CODE, ".claude/rules/<path>.md");

        ToolPathCount count = ToolPaths.count(rules, counts(rules.source(), 3));

        assertEquals(ToolPathState.LINK_MISSING, count.state());
        assertEquals(3, count.count());
    }

    @Test
    void count_linkWithoutSource_isNotDelivered() {
        ToolPath rules = read(AgentTool.CLAUDE_CODE, ".claude/rules/<path>.md");

        ToolPathCount count = ToolPaths.count(rules, counts(rules.link(), 1));

        assertEquals(ToolPathState.NOT_DELIVERED, count.state());
        assertEquals(0, count.count());
    }

    @Test
    void count_regularFileAtTheReadPath_isDirectWithItsCount() {
        ToolPath agentsFile = read(AgentTool.CODEX, "AGENTS.md");

        ToolPathCount count = ToolPaths.count(agentsFile, counts(agentsFile.direct(), 1, agentsFile.source(), 2));

        assertEquals(ToolPathState.DIRECT, count.state());
        assertEquals(1, count.count());
    }

    @Test
    void count_readWithoutLink_isDirectWhenItsSourceCounts() {
        ToolPath roles = read(AgentTool.CURSOR, ".agents/roles/<path>.md");

        assertEquals(ToolPathState.DIRECT, ToolPaths.count(roles, counts(roles.source(), 2)).state());
        assertEquals(ToolPathState.NOT_DELIVERED, ToolPaths.count(roles, counts()).state());
    }

    @Test
    void count_noScope_leavesStateAndCountNull() {
        ToolPathCount count = ToolPaths.count(read(AgentTool.GEMINI_CLI, "AGENTS.md"), null);

        assertNull(count.state());
        assertNull(count.count());
    }

    private static ToolPath read(AgentTool tool, String readPath) {
        return ToolPaths.pathsOf(tool).stream().filter(path -> path.readPath().equals(readPath)).findFirst().orElseThrow();
    }

    private static PathMapping agents(AgentsFilePlacement placement) {
        return DeliveryPaths.mappingOf(FileKind.AGENTS_FILE, null, placement, null);
    }

    private static Map<PathMapping, Integer> counts(Object... rowsAndCounts) {
        Map<PathMapping, Integer> counts = new HashMap<>();
        for (int index = 0; index + 1 < rowsAndCounts.length; index += 2) {
            counts.put((PathMapping) rowsAndCounts[index], (Integer) rowsAndCounts[index + 1]);
        }
        return counts;
    }
}
