package com.korur.oba.apps.kaya.render.service.impl;

// 1. Framework-level

// 2. Third-party
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.dto.embedded.PathMapping;
import com.korur.oba.apps.kaya.render.dto.embedded.PathMappingCount;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.DeliveryPaths;
import com.korur.oba.apps.kaya.render.mock_data.PathMappingRows;
import com.korur.oba.apps.kaya.render.mock_data.RenderStore;
import com.korur.oba.apps.kaya.render.mock_data.RenderTrees;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

import static com.korur.oba.apps.kaya.render.mock_data.RenderStore.utf8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The counts of {@link RenderQueryServiceImpl#getPathMappings}: one per delivered file of the row,
 * a shared skill part once per skill, none for the AGENTS.md of a mounted pack (a stored agents file is a
 * root one, the mount moves it only on delivery), nothing for another organization, and null counts without a version.
 */
class RenderQueryServicePathMappingUnitTest {

    private RenderStore store;

    @BeforeEach
    void setUp() {
        store = new RenderStore();
    }

    @Test
    void getPathMappings_versionWithEveryKind_countsEachRowAsRenderDelivers() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, everyKind());

        // Act
        Map<PathMapping, Integer> counts = counts(store.organizationId, List.of(store.versionId));

        // Assert
        assertEquals(1, counts.get(agents(AgentsFilePlacement.ROOT)));
        assertEquals(1, counts.get(agents(AgentsFilePlacement.AGENTS_DIR)));
        assertEquals(0, counts.get(agents(AgentsFilePlacement.MOUNT_ROOT)));
        assertEquals(2, counts.get(kind(FileKind.RULE)));
        assertEquals(2, counts.get(kind(FileKind.CONVENTION)));
        assertEquals(2, counts.get(kind(FileKind.WORKFLOW)));
        assertEquals(2, counts.get(kind(FileKind.HOOK)));
        assertEquals(1, counts.get(kind(FileKind.SKILL)));
        assertEquals(1, counts.get(kind(FileKind.SKILL_LINK)));
        assertEquals(1, counts.get(toolLink(ToolLinkName.CLAUDE_RULES)));
        assertEquals(1, counts.get(toolLink(ToolLinkName.CLAUDE_TEMPLATES)));
        assertEquals(0, counts.get(toolLink(ToolLinkName.ROOT_AGENTS_FILE)));
        for (SkillPartKind part : SkillPartKind.values()) {
            assertEquals(1, counts.get(DeliveryPaths.mappingOf(FileKind.SKILL, part, null, null)), part.name());
        }
        assertEquals(renderedCounts(), counts);
    }

    @Test
    void getPathMappings_partSharedByTwoSkills_countsOncePerSkill() {
        // Arrange
        byte[] shared = utf8("# Shared\n");
        store.renderService.parse(store.organizationId, store.versionId, List.of(
                skillFile("alpha"), SourceFile.of(".agents/skills/alpha/references/shared.md", FileMode.FILE, shared),
                skillFile("beta"), SourceFile.of(".agents/skills/beta/references/shared.md", FileMode.FILE, shared)));

        // Act
        Map<PathMapping, Integer> counts = counts(store.organizationId, List.of(store.versionId));

        // Assert
        assertEquals(1, store.references.size());
        assertEquals(2, counts.get(DeliveryPaths.mappingOf(FileKind.SKILL, SkillPartKind.REFERENCE, null, null)));
        assertEquals(2, counts.get(kind(FileKind.SKILL)));
        assertEquals(2, counts.get(kind(FileKind.SKILL_LINK)));
    }

    @Test
    void getPathMappings_versionOfAnotherOrganization_countsZero() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, everyKind());

        // Act
        List<PathMappingCount> rows = store.renderQueryService.getPathMappings(UUID.randomUUID(), List.of(store.versionId));

        // Assert
        assertEquals(22, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.count() == 0));
    }

    @Test
    void getPathMappings_noVersion_returnsEveryRowWithNullCounts() {
        List<PathMappingCount> rows = store.renderQueryService.getPathMappings(store.organizationId, List.of());

        assertEquals(DeliveryPaths.mappings(), rows.stream().map(PathMappingCount::mapping).toList());
        rows.forEach(row -> assertNull(row.count()));
    }

    private Map<PathMapping, Integer> counts(UUID organizationId, List<UUID> versionIds) {
        Map<PathMapping, Integer> counts = new LinkedHashMap<>();
        store.renderQueryService.getPathMappings(organizationId, versionIds).forEach(row -> counts.put(row.mapping(), row.count()));
        return counts;
    }

    // The rendered files of the store's version, counted per row through the path rules.
    private Map<PathMapping, Integer> renderedCounts() {
        Map<PathMapping, Integer> counts = new LinkedHashMap<>();
        DeliveryPaths.mappings().forEach(mapping -> counts.put(mapping, 0));
        for (RenderedFile file : store.renderQueryService.render(store.organizationId, store.versionId)) {
            counts.merge(PathMappingRows.rowOf(file.path(), file.mode(), null).orElseThrow(), 1, Integer::sum);
        }
        return counts;
    }

    private static PathMapping agents(AgentsFilePlacement placement) {
        return DeliveryPaths.mappingOf(FileKind.AGENTS_FILE, null, placement, null);
    }

    private static PathMapping toolLink(ToolLinkName link) {
        return DeliveryPaths.mappingOf(FileKind.TOOL_LINK, null, null, link);
    }

    private static PathMapping kind(FileKind kind) {
        return DeliveryPaths.mappingOf(kind, null, null, null);
    }

    private static List<SourceFile> everyKind() {
        List<SourceFile> tree = new ArrayList<>(RenderTrees.base());
        tree.add(SourceFile.of(".agents/workflows/ship.md", FileMode.FILE, utf8("# Ship\n")));
        tree.add(SourceFile.of(".agents/rules/guardrails/protect-secrets.md", FileMode.FILE, utf8("---\ncategory: SECURITY\n"
                + "description: Keep secrets out of sessions\ndeny:\n  - Read(./.env)\n---\nNever read secret files.\n")));
        tree.add(new SourceFile(".claude/rules", FileMode.SYMLINK, "../.agents/rules", null, null));
        tree.add(new SourceFile(".claude/templates", FileMode.SYMLINK, "../.agents/templates", null, null));
        return tree;
    }

    private static SourceFile skillFile(String name) {
        return SourceFile.of(".agents/skills/" + name + "/SKILL.md", FileMode.FILE,
                utf8("---\nname: " + name + "\ndescription: The " + name + " skill\n---\n\n# " + name + "\n"));
    }
}
