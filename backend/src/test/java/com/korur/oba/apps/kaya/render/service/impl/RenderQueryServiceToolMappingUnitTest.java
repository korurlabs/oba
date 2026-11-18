package com.korur.oba.apps.kaya.render.service.impl;

// 1. Framework-level

// 2. Third-party
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolMapping;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolPathCount;
import com.korur.oba.apps.kaya.render.enums.AgentTool;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.enums.ToolPathState;
import com.korur.oba.apps.kaya.render.helper.ToolPaths;
import com.korur.oba.apps.kaya.render.mock_data.RenderStore;
import com.korur.oba.apps.kaya.render.mock_data.RenderTrees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RenderQueryServiceImpl#getToolMappings}: one entry per tool in enum order with the reads of
 * {@link ToolPaths}, each read's state from the version's path mapping counts, nothing for another
 * organization and null states without a version.
 */
class RenderQueryServiceToolMappingUnitTest {

    private RenderStore store;

    @BeforeEach
    void setUp() {
        store = new RenderStore();
    }

    @Test
    void getToolMappings_versionWithTheToolLinks_showsClaudeCodeReadingThroughThem() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, linkedTree());

        // Act
        List<ToolMapping> tools = store.renderQueryService.getToolMappings(store.organizationId, List.of(store.versionId));

        // Assert
        assertEquals(Arrays.asList(AgentTool.values()), tools.stream().map(ToolMapping::tool).toList());
        ToolPathCount rules = read(tools, AgentTool.CLAUDE_CODE, ".claude/rules/<path>.md");
        assertEquals(ToolPathState.LINKED, rules.state());
        assertEquals(2, rules.count());
        assertEquals(ToolPathState.LINKED, read(tools, AgentTool.CLAUDE_CODE, "AGENTS.md").state());
        assertEquals(ToolPathState.LINKED, read(tools, AgentTool.CLAUDE_CODE, ".claude/skills/<name>/SKILL.md").state());
        assertEquals(ToolPathState.LINKED, read(tools, AgentTool.CLAUDE_CODE, ".claude/templates/<path>.md").state());
        assertEquals(ToolPathState.DIRECT, read(tools, AgentTool.CODEX, ".agents/rules/<path>.md").state());
        assertEquals(ToolPathState.NOT_DELIVERED, read(tools, AgentTool.CODEX, "<rootPath>/AGENTS.md").state());
    }

    @Test
    void getToolMappings_versionWithoutTheLinks_showsTheLinksMissingAndTheRegularAgentsFileDirect() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, RenderTrees.base());

        // Act
        List<ToolMapping> tools = store.renderQueryService.getToolMappings(store.organizationId, List.of(store.versionId));

        // Assert
        assertEquals(ToolPathState.LINK_MISSING, read(tools, AgentTool.CLAUDE_CODE, ".claude/rules/<path>.md").state());
        assertEquals(ToolPathState.LINK_MISSING, read(tools, AgentTool.CLAUDE_CODE, ".claude/templates/<path>.md").state());
        ToolPathCount agentsFile = read(tools, AgentTool.CLAUDE_CODE, "AGENTS.md");
        assertEquals(ToolPathState.DIRECT, agentsFile.state());
        assertEquals(1, agentsFile.count());
    }

    @Test
    void getToolMappings_versionOfAnotherOrganization_deliversNothing() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, linkedTree());

        // Act
        List<ToolMapping> tools = store.renderQueryService.getToolMappings(UUID.randomUUID(), List.of(store.versionId));

        // Assert
        assertTrue(tools.stream().flatMap(tool -> tool.reads().stream())
                .allMatch(read -> read.state() == ToolPathState.NOT_DELIVERED && read.count() == 0));
    }

    @Test
    void getToolMappings_noVersion_listsEveryReadWithNullStateAndCount() {
        List<ToolMapping> tools = store.renderQueryService.getToolMappings(store.organizationId, List.of());

        for (ToolMapping tool : tools) {
            assertEquals(ToolPaths.pathsOf(tool.tool()), tool.reads().stream().map(ToolPathCount::path).toList());
            tool.reads().forEach(read -> {
                assertNull(read.state());
                assertNull(read.count());
            });
        }
    }

    private static ToolPathCount read(List<ToolMapping> tools, AgentTool tool, String readPath) {
        return tools.stream().filter(mapping -> mapping.tool() == tool).findFirst().orElseThrow().reads().stream()
                .filter(read -> read.path().readPath().equals(readPath)).findFirst().orElseThrow();
    }

    // The base tree with AGENTS.md as the link to .agents/AGENTS.md and the two .claude links.
    private static List<SourceFile> linkedTree() {
        List<SourceFile> tree = new ArrayList<>(RenderTrees.base());
        tree.removeIf(file -> file.path().equals("AGENTS.md"));
        tree.add(new SourceFile("AGENTS.md", FileMode.SYMLINK, ".agents/AGENTS.md", null, null));
        tree.add(new SourceFile(".claude/rules", FileMode.SYMLINK, "../.agents/rules", null, null));
        tree.add(new SourceFile(".claude/templates", FileMode.SYMLINK, "../.agents/templates", null, null));
        return tree;
    }
}
