package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.dto.embedded.PathMapping;
import com.korur.oba.apps.kaya.render.enums.FileFormat;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.mock_data.PathMappingRows;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;
import com.korur.oba.apps.kaya.validation.helper.MountPaths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The path mapping rows against the path rules: an example path of every row classifies back to that
 * row, and every kind and skill part has one, so a kind added without its row fails here.
 */
class PathMappingsUnitTest {

    private static final String MOUNT_FOLDER = "projects/kagi";
    private static final String MOUNTED_PACK = "kagi";

    // One example per row, in the order of the rows.
    private record Example(String path, FileMode mode, String projectRootPath) {
    }

    private static final List<Example> EXAMPLES = List.of(
            new Example("AGENTS.md", FileMode.FILE, null),
            new Example(".agents/AGENTS.md", FileMode.FILE, null),
            new Example(MOUNT_FOLDER + "/AGENTS.md", FileMode.FILE, MOUNT_FOLDER),
            new Example(".agents/rules/10-backend.md", FileMode.FILE, null),
            new Example(".agents/rules/guardrails/protect-secrets.md", FileMode.FILE, null),
            new Example("docs/private/conventions/backend/naming.md", FileMode.FILE, null),
            new Example(".agents/roles/coder.md", FileMode.FILE, null),
            new Example(".agents/workflows/implement.md", FileMode.FILE, null),
            new Example(".agents/templates/HANDOFF.md", FileMode.FILE, null),
            new Example(".agents/hooks/guard.sh", FileMode.EXECUTABLE, null),
            new Example(".claude/agents/reviewer.md", FileMode.FILE, null),
            new Example(".claude/settings.json", FileMode.FILE, null),
            new Example(".agents/skills/kazan/SKILL.md", FileMode.FILE, null),
            new Example(".agents/skills/kazan/workflows/close.md", FileMode.FILE, null),
            new Example(".agents/skills/kazan/scripts/run.sh", FileMode.EXECUTABLE, null),
            new Example(".agents/skills/kazan/conventions/style.md", FileMode.FILE, null),
            new Example(".agents/skills/kazan/agents/openai.yml", FileMode.FILE, null),
            new Example(".agents/skills/kazan/references/api.md", FileMode.FILE, null),
            new Example(".claude/skills/kazan", FileMode.SYMLINK, null),
            new Example("AGENTS.md", FileMode.SYMLINK, null),
            new Example(".claude/rules", FileMode.SYMLINK, null),
            new Example(".claude/templates", FileMode.SYMLINK, null));

    @Test
    void mappings_examplePathOfEveryRow_classifiesToThatRow() {
        List<PathMapping> mappings = DeliveryPaths.mappings();

        assertEquals(22, mappings.size());
        assertEquals(EXAMPLES.size(), mappings.size());
        for (int i = 0; i < mappings.size(); i++) {
            Example example = EXAMPLES.get(i);
            assertEquals(mappings.get(i), PathMappingRows.rowOf(example.path(), example.mode(), example.projectRootPath()).orElseThrow(),
                    example.path());
        }
    }

    @Test
    void mappings_otherPathsOfASkill_classifyToTheirPart() {
        assertEquals(SkillPartKind.CONVENTION, part(".agents/skills/kazan/rules/naming.md", FileMode.FILE));
        assertEquals(SkillPartKind.TOOL_PARAMETER, part(".agents/skills/kazan/evals/evals.json", FileMode.FILE));
        assertEquals(SkillPartKind.SCRIPT, part(".agents/skills/kazan/references/tool.sh", FileMode.EXECUTABLE));
        assertEquals(SkillPartKind.REFERENCE, part(".agents/skills/kazan/notes.md", FileMode.FILE));
    }

    @Test
    void mappings_everyKindAndPart_hasARow() {
        Set<FileKind> kinds = new HashSet<>();
        Set<SkillPartKind> parts = new HashSet<>();
        Set<AgentsFilePlacement> placements = new HashSet<>();
        Set<ToolLinkName> links = new HashSet<>();
        for (PathMapping mapping : DeliveryPaths.mappings()) {
            if (mapping.link() != null) {
                links.add(mapping.link());
            }
            kinds.add(mapping.kind());
            if (mapping.part() != null) {
                parts.add(mapping.part());
            }
            if (mapping.placement() != null) {
                placements.add(mapping.placement());
            }
        }

        assertEquals(Set.of(FileKind.values()), kinds);
        assertEquals(Set.of(SkillPartKind.values()), parts);
        assertEquals(Set.copyOf(Arrays.asList(AgentsFilePlacement.values())), placements);
        assertEquals(Set.of(ToolLinkName.values()), links);
    }

    @Test
    void mappings_toolLinkRows_holdTheirPathAndTargetAsLinks() {
        // Act
        List<PathMapping> rows = DeliveryPaths.mappings().stream().filter(mapping -> mapping.kind() == FileKind.TOOL_LINK).toList();

        // Assert
        assertEquals(List.of("AGENTS.md", ".claude/rules", ".claude/templates"), rows.stream().map(PathMapping::pathPattern).toList());
        for (PathMapping row : rows) {
            assertEquals(List.of(FileFormat.SYMLINK), row.formats(), row.pathPattern());
            assertFalse(row.executable(), row.pathPattern());
            assertTrue(row.note().contains(DeliveryPaths.toolLinkTarget(row.link())), row.note());
            assertEquals(row, DeliveryPaths.mappingOf(FileKind.TOOL_LINK, null, null, row.link()));
        }
        // Each other row carries no link.
        assertTrue(DeliveryPaths.mappings().stream().filter(mapping -> mapping.kind() != FileKind.TOOL_LINK)
                .allMatch(mapping -> mapping.link() == null));
    }

    @Test
    void mappings_executableRows_areTheChangesAnOwnerApproves() {
        List<String> executable = DeliveryPaths.mappings().stream().filter(PathMapping::executable).map(PathMapping::pathPattern).toList();

        assertEquals(List.of(".agents/rules/guardrails/<name>.md", ".agents/hooks/<path>", ".claude/settings.json", ".agents/skills/<name>/scripts/<path>"),
                executable);
    }

    @Test
    void mappings_patternsAndFormats_matchTheirKinds() {
        PathMapping toolParameter = DeliveryPaths.mappingOf(FileKind.SKILL, SkillPartKind.TOOL_PARAMETER, null, null);
        PathMapping link = DeliveryPaths.mappingOf(FileKind.SKILL_LINK, null, null, null);

        assertEquals(".agents/rules/<path>.md", DeliveryPaths.mappingOf(FileKind.RULE, null, null, null).pathPattern());
        assertEquals("<rootPath>/AGENTS.md",
                DeliveryPaths.mappingOf(FileKind.AGENTS_FILE, null, AgentsFilePlacement.MOUNT_ROOT, null).pathPattern());
        assertEquals(List.of(FileFormat.JSON, FileFormat.YAML), toolParameter.formats());
        assertEquals(".claude/skills/<name>", link.pathPattern());
        assertEquals(List.of(FileFormat.SYMLINK), link.formats());
        assertFalse(link.executable());
    }

    @Test
    void mappings_mountedPatterns_lieInTheMountedPacksNamespace() {
        for (PathMapping mapping : DeliveryPaths.mappings()) {
            if (mapping.mountedPattern() == null) {
                assertFalse(MountPaths.isInNamespace(example(mapping.pathPattern()), MOUNTED_PACK, MOUNT_FOLDER),
                        mapping.pathPattern() + " has no mounted pattern, so a mounted pack may not deliver it");
                continue;
            }
            assertTrue(MountPaths.isInNamespace(example(mapping.mountedPattern()), MOUNTED_PACK, MOUNT_FOLDER),
                    mapping.mountedPattern());
        }
    }

    @Test
    void mappings_mountedPatterns_nameThePackNamespace() {
        assertEquals(".agents/rules/<pack>/<path>.md", DeliveryPaths.mappingOf(FileKind.RULE, null, null, null).mountedPattern());
        assertEquals("<rootPath>/AGENTS.md",
                DeliveryPaths.mappingOf(FileKind.AGENTS_FILE, null, AgentsFilePlacement.ROOT, null).mountedPattern());
        assertEquals(".agents/skills/<pack>-<name>/scripts/<path>",
                DeliveryPaths.mappingOf(FileKind.SKILL, SkillPartKind.SCRIPT, null, null).mountedPattern());
        assertEquals(".claude/skills/<pack>-<name>", DeliveryPaths.mappingOf(FileKind.SKILL_LINK, null, null, null).mountedPattern());
        assertEquals("docs/private/conventions/projects/<pack>/<path>.md",
                DeliveryPaths.mappingOf(FileKind.CONVENTION, null, null, null).mountedPattern());
        assertNull(DeliveryPaths.mappingOf(FileKind.AGENTS_FILE, null, AgentsFilePlacement.AGENTS_DIR, null).mountedPattern());
        assertNull(DeliveryPaths.mappingOf(FileKind.SETTINGS, null, null, null).mountedPattern());
        assertNull(DeliveryPaths.mappingOf(FileKind.GUARDRAIL, null, null, null).mountedPattern());
    }

    /** A path a pattern stands for, with each placeholder and brace group filled in. */
    private static String example(String pattern) {
        return pattern.replace("<pack>", MOUNTED_PACK).replace("<rootPath>", MOUNT_FOLDER).replace("<name>", "tool")
                .replace("<path>", "deep/file").replace("{rules,conventions}", "rules").replace(".{json,yaml,yml}", ".json");
    }

    @Test
    void mappingOf_noSuchRow_throws() {
        assertThrows(IllegalArgumentException.class, () -> DeliveryPaths.mappingOf(FileKind.RULE, SkillPartKind.SCRIPT, null, null));
    }

    private static SkillPartKind part(String path, FileMode mode) {
        return PathMappingRows.rowOf(path, mode, null).orElseThrow().part();
    }
}
