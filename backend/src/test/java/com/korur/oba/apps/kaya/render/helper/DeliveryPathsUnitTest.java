package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party
import org.junit.jupiter.api.Test;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryPathsUnitTest {

    @Test
    void classify_deliveredPaths_returnsTheirKind() {
        assertEquals(FileKind.AGENTS_FILE, DeliveryPaths.classify("AGENTS.md", null).orElseThrow().kind());
        assertEquals(AgentsFilePlacement.MOUNT_ROOT,
                DeliveryPaths.classify("projects/kaya/AGENTS.md", "projects/kaya").orElseThrow().placement());
        assertEquals(FileKind.RULE, DeliveryPaths.classify(".agents/rules/10-backend.md", null).orElseThrow().kind());
        assertEquals("10-backend.md", DeliveryPaths.classify(".agents/rules/10-backend.md", null).orElseThrow().relativePath());
        assertEquals(FileKind.GUARDRAIL, DeliveryPaths.classify(".agents/rules/guardrails/protect-secrets.md", null).orElseThrow().kind());
        assertEquals("protect-secrets.md",
                DeliveryPaths.classify(".agents/rules/guardrails/protect-secrets.md", null).orElseThrow().relativePath());
        assertEquals(FileKind.HOOK, DeliveryPaths.classify(".agents/hooks/guard.sh", null).orElseThrow().kind());
        assertEquals(FileKind.SETTINGS, DeliveryPaths.classify(".claude/settings.json", null).orElseThrow().kind());
        assertEquals(FileKind.SKILL, DeliveryPaths.classify(".agents/skills/shadcn/assets/a.png", null).orElseThrow().kind());
        assertEquals(FileKind.SKILL_LINK, DeliveryPaths.classify(".claude/skills/shadcn", null).orElseThrow().kind());
        assertEquals(FileKind.CONVENTION,
                DeliveryPaths.classify("docs/private/conventions/backend/naming.md", null).orElseThrow().kind());
    }

    @Test
    void classify_undeliveredPaths_returnsEmpty() {
        assertTrue(DeliveryPaths.classify("src/Main.java", null).isEmpty());
        assertTrue(DeliveryPaths.classify(".agents/rules/notes.txt", null).isEmpty());
        assertTrue(DeliveryPaths.classify("projects/kaya/AGENTS.md", null).isEmpty());
        assertTrue(DeliveryPaths.classify(".agents/pipelines/feature.yaml", null).isEmpty());
        assertTrue(DeliveryPaths.classify(".agents/pipelines/kaya/feature.yaml", null).isEmpty());
        assertTrue(DeliveryPaths.classify(".agents/pipelines/feature.yml", null).isEmpty());
        assertTrue(DeliveryPaths.classify(".agents/pipelines/feature.md", null).isEmpty());
        assertTrue(DeliveryPaths.classify(".claude/skills/shadcn/SKILL.md", null).isEmpty());
        assertTrue(DeliveryPaths.classify(".mcp.json", null).isEmpty());
    }

    @Test
    void classify_toolLinkAsSymlink_isTheToolLink() {
        for (ToolLinkName name : ToolLinkName.values()) {
            DeliveryPath delivery = DeliveryPaths.classify(DeliveryPaths.toolLinkPath(name), null, true).orElseThrow();
            assertEquals(FileKind.TOOL_LINK, delivery.kind(), name.name());
            assertEquals(name, delivery.toolLink());
            assertNull(delivery.entityKind(null), name.name());
        }
    }

    @Test
    void classify_agentsFileAsFile_isTheRootAgentsFile() {
        DeliveryPath delivery = DeliveryPaths.classify("AGENTS.md", null, false).orElseThrow();

        assertEquals(FileKind.AGENTS_FILE, delivery.kind());
        assertEquals(AgentsFilePlacement.ROOT, delivery.placement());
        assertNull(delivery.toolLink());
    }

    @Test
    void classify_claudeLinksAsFiles_stayToolLinksAndNothingBelowThemIsDelivered() {
        assertEquals(FileKind.TOOL_LINK, DeliveryPaths.classify(".claude/rules", null, false).orElseThrow().kind());
        assertEquals(FileKind.TOOL_LINK, DeliveryPaths.classify(".claude/templates", null).orElseThrow().kind());
        assertTrue(DeliveryPaths.classify(".claude/rules/x.md", null, false).isEmpty());
        assertTrue(DeliveryPaths.classify(".claude/templates/HANDOFF.md", null, true).isEmpty());
    }

    @Test
    void toolLinkOf_fixedPaths_returnTheirNameAndTarget() {
        assertEquals(ToolLinkName.ROOT_AGENTS_FILE, DeliveryPaths.toolLinkOf("AGENTS.md").orElseThrow());
        assertEquals(ToolLinkName.CLAUDE_RULES, DeliveryPaths.toolLinkOf(".claude/rules").orElseThrow());
        assertEquals(".agents/AGENTS.md", DeliveryPaths.toolLinkTarget(ToolLinkName.ROOT_AGENTS_FILE));
        assertEquals("../.agents/rules", DeliveryPaths.toolLinkTarget(ToolLinkName.CLAUDE_RULES));
        assertEquals("../.agents/templates", DeliveryPaths.toolLinkTarget(ToolLinkName.CLAUDE_TEMPLATES));
        assertTrue(DeliveryPaths.toolLinkOf(".agents/AGENTS.md").isEmpty());
    }
}
