package com.korur.oba.apps.kaya.render.service.impl;

// 1. Framework-level

// 2. Third-party
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.FileRefusal;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.DeliveryPaths;
import com.korur.oba.apps.kaya.render.mock_data.RenderStore;
import com.korur.oba.apps.kaya.toollink.entity.ToolLink;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;
import com.korur.oba.apps.kaya.validation.helper.PathRules;

import static com.korur.oba.apps.kaya.render.mock_data.RenderStore.utf8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tool links AGENTS.md, .claude/rules and .claude/templates through parse, render and screen: each
 * is kept as one row by name, rendered as a symlink with its fixed target, and the root AGENTS.md is a
 * file or the link, never both.
 */
class RenderToolLinkUnitTest {

    private static final String AGENTS_FILE = "AGENTS.md";
    private static final String RULES_LINK = ".claude/rules";
    private static final String TEMPLATES_LINK = ".claude/templates";

    private RenderStore store;

    @BeforeEach
    void setUp() {
        store = new RenderStore();
    }

    @Test
    void parse_treeWithTheThreeLinks_storesOneRowPerLinkAndRendersThemAsSymlinks() {
        // Act
        store.renderService.parse(store.organizationId, store.versionId, linkedTree());

        // Assert
        assertEquals(List.of(ToolLinkName.ROOT_AGENTS_FILE, ToolLinkName.CLAUDE_RULES, ToolLinkName.CLAUDE_TEMPLATES),
                store.toolLinks.stream().map(ToolLink::getName).sorted().toList());
        assertTrue(store.agentsFiles.stream().noneMatch(file -> file.getPlacement() == AgentsFilePlacement.ROOT));
        Map<String, RenderedFile> files = render();
        for (ToolLinkName name : ToolLinkName.values()) {
            RenderedFile link = files.get(DeliveryPaths.toolLinkPath(name));
            assertEquals(FileMode.SYMLINK, link.mode(), name.name());
            assertEquals(DeliveryPaths.toolLinkTarget(name), link.symlinkTarget(), name.name());
            assertNull(link.content(), name.name());
            assertNull(link.entityKind(), name.name());
            assertNull(link.entityId(), name.name());
        }
    }

    @Test
    void parse_treeWithoutLinks_removesTheStoredLinks() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, linkedTree());

        // Act
        store.renderService.parse(store.organizationId, store.versionId, List.of(
                SourceFile.of(".agents/AGENTS.md", FileMode.FILE, utf8("# Agent tools\n"))));

        // Assert
        assertTrue(store.toolLinks.isEmpty());
        assertTrue(render().values().stream().noneMatch(file -> file.mode() == FileMode.SYMLINK));
    }

    @Test
    void parseOne_linkWithAnotherTarget_throwsInvalidFileWithTheFixedTarget() {
        // Act
        ValidationException exception = assertThrows(ValidationException.class, () -> store.renderService.parseOne(
                store.organizationId, store.versionId, link(RULES_LINK, "../.agents/roles")));

        // Assert
        assertEquals(ExceptionDomainKayaError.RENDER_INVALID_FILE.getCode(), exception.getErrorCode());
        assertEquals("the link .claude/rules must point at ../.agents/rules", exception.getErrorValues().get(RenderConstant.FIELD_REASON));
        assertTrue(store.toolLinks.isEmpty());
    }

    @Test
    void parseOne_pathBelowALinkOrAFileAtALink_throwsNotDeliverable() {
        // Arrange
        List<SourceFile> files = List.of(
                SourceFile.of(RULES_LINK + "/x.md", FileMode.FILE, utf8("# X\n")),
                link(RULES_LINK + "/x.md", "../../.agents/rules/x.md"),
                SourceFile.of(RULES_LINK, FileMode.FILE, utf8("# Rules\n")));

        // Act and Assert
        for (SourceFile file : files) {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> store.renderService.parseOne(store.organizationId, store.versionId, file));
            assertEquals(ExceptionDomainKayaError.RENDER_NOT_DELIVERABLE.getCode(), exception.getErrorCode(), file.path());
        }
        assertTrue(store.toolLinks.isEmpty());
    }

    @Test
    void parseOne_agentsLinkSaved_deletesTheRootAgentsFile() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, List.of(
                SourceFile.of(AGENTS_FILE, FileMode.FILE, utf8("# Agents\n")),
                SourceFile.of(".agents/AGENTS.md", FileMode.FILE, utf8("# Agent tools\n"))));

        // Act
        store.renderService.parseOne(store.organizationId, store.versionId, link(AGENTS_FILE, ".agents/AGENTS.md"));

        // Assert
        assertTrue(store.agentsFiles.stream().noneMatch(file -> file.getPlacement() == AgentsFilePlacement.ROOT));
        assertEquals(List.of(ToolLinkName.ROOT_AGENTS_FILE), store.toolLinks.stream().map(ToolLink::getName).toList());
        RenderedFile agents = render().get(AGENTS_FILE);
        assertEquals(FileMode.SYMLINK, agents.mode());
        assertEquals(".agents/AGENTS.md", agents.symlinkTarget());
    }

    @Test
    void parseOne_agentsFileSaved_deletesTheAgentsLink() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, linkedTree());

        // Act
        store.renderService.parseOne(store.organizationId, store.versionId, SourceFile.of(AGENTS_FILE, FileMode.FILE,
                utf8("# Agents\n")));

        // Assert
        assertTrue(store.toolLinks.stream().noneMatch(link -> link.getName() == ToolLinkName.ROOT_AGENTS_FILE));
        assertEquals(1, store.agentsFiles.stream().filter(file -> file.getPlacement() == AgentsFilePlacement.ROOT).count());
        assertEquals(FileMode.FILE, render().get(AGENTS_FILE).mode());
    }

    @Test
    void parseOne_agentsFileRemoved_removesTheLinkAndTheFile() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, linkedTree());

        // Act
        store.renderService.parseOne(store.organizationId, store.versionId, SourceFile.removal(AGENTS_FILE));

        // Assert
        assertTrue(store.toolLinks.stream().noneMatch(link -> link.getName() == ToolLinkName.ROOT_AGENTS_FILE));
        assertTrue(store.agentsFiles.stream().noneMatch(file -> file.getPlacement() == AgentsFilePlacement.ROOT));
        assertNull(render().get(AGENTS_FILE));
    }

    @Test
    void parseOne_claudeLinkSavedAndRemoved_savesThenDeletesItsRow() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, List.of(
                SourceFile.of(".agents/templates/HANDOFF.md", FileMode.FILE, utf8("# Handoff\n"))));

        // Act
        store.renderService.parseOne(store.organizationId, store.versionId, link(TEMPLATES_LINK, "../.agents/templates"));
        List<ToolLinkName> saved = store.toolLinks.stream().map(ToolLink::getName).toList();
        store.renderService.parseOne(store.organizationId, store.versionId, SourceFile.removal(TEMPLATES_LINK));

        // Assert
        assertEquals(List.of(ToolLinkName.CLAUDE_TEMPLATES), saved);
        assertTrue(store.toolLinks.isEmpty());
        assertNull(render().get(TEMPLATES_LINK));
    }

    @Test
    void renderPath_agentsFileOfAVersionWithTheLink_answersTheLink() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, linkedTree());

        // Act
        Map<UUID, RenderedFile> agents = store.renderQueryService.renderPath(store.organizationId, List.of(store.versionId),
                AGENTS_FILE);
        Map<UUID, RenderedFile> rules = store.renderQueryService.renderPath(store.organizationId, List.of(store.versionId),
                RULES_LINK);

        // Assert
        assertEquals(".agents/AGENTS.md", agents.get(store.versionId).symlinkTarget());
        assertEquals("../.agents/rules", rules.get(store.versionId).symlinkTarget());
        assertTrue(store.renderQueryService.renderPath(UUID.randomUUID(), List.of(store.versionId), RULES_LINK).isEmpty());
    }

    @Test
    void screen_linkWithAnotherTarget_refusesOnlyThatLink() {
        // Arrange
        List<SourceFile> files = new ArrayList<>(linkedTree());
        files.add(link(TEMPLATES_LINK, "../.agents/rules"));
        files.removeIf(file -> file.path().equals(TEMPLATES_LINK) && "../.agents/templates".equals(file.symlinkTarget()));

        // Act
        List<FileRefusal> refusals = store.renderQueryService.screen(files);

        // Assert
        assertEquals(1, refusals.size());
        assertEquals(TEMPLATES_LINK, refusals.get(0).path());
        assertEquals(ExceptionDomainKayaError.RENDER_INVALID_FILE.getCode(), refusals.get(0).errorCode());
    }

    @Test
    void countEntities_toolLinks_countAsNoEntity() {
        // Act
        Map<EntityKind, Integer> counts = store.renderQueryService.countEntities(linkedTree());

        // Assert
        // Only .agents/AGENTS.md is an agents file; the link at AGENTS.md is none.
        assertEquals(1, counts.get(EntityKind.AGENTS_FILE));
        assertEquals(1, counts.get(EntityKind.RULE));
        assertEquals(1, counts.get(EntityKind.TEMPLATE));
    }

    @Test
    void toolLinkTargets_resolveToTheFolderOfTheirSource() {
        assertEquals(".agents/AGENTS.md", PathRules.resolveSymlink(AGENTS_FILE, DeliveryPaths.toolLinkTarget(ToolLinkName.ROOT_AGENTS_FILE))
                .orElseThrow());
        assertEquals(RenderConstant.RULES_FOLDER, PathRules.resolveSymlink(RULES_LINK,
                DeliveryPaths.toolLinkTarget(ToolLinkName.CLAUDE_RULES)).orElseThrow() + RenderConstant.PATH_SEPARATOR);
        assertEquals(RenderConstant.TEMPLATES_FOLDER, PathRules.resolveSymlink(TEMPLATES_LINK,
                DeliveryPaths.toolLinkTarget(ToolLinkName.CLAUDE_TEMPLATES)).orElseThrow() + RenderConstant.PATH_SEPARATOR);
    }

    // The pack layout: the tool-neutral source under .agents/ and the three links that point into it.
    private static List<SourceFile> linkedTree() {
        return List.of(
                link(AGENTS_FILE, ".agents/AGENTS.md"),
                SourceFile.of(".agents/AGENTS.md", FileMode.FILE, utf8("# Agent tools\n")),
                SourceFile.of(".agents/rules/core.md", FileMode.FILE, utf8("Core rule for every file.\n")),
                SourceFile.of(".agents/templates/HANDOFF.md", FileMode.FILE, utf8("# Handoff\n")),
                link(RULES_LINK, "../.agents/rules"),
                link(TEMPLATES_LINK, "../.agents/templates"));
    }

    private static SourceFile link(String path, String target) {
        return new SourceFile(path, FileMode.SYMLINK, target, null, null);
    }

    private Map<String, RenderedFile> render() {
        Map<String, RenderedFile> files = new HashMap<>();
        store.renderQueryService.render(store.organizationId, store.versionId).forEach(file -> files.put(file.path(), file));
        return files;
    }
}
