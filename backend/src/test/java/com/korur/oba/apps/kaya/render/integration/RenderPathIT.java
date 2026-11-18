package com.korur.oba.apps.kaya.render.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 3. Java SDK
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.mock_data.RenderTrees;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillPartLink;
import com.korur.oba.apps.kaya.skill.service.SkillQueryService;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendering one path in several versions gives, for each version, exactly the entry a whole render
 * of that version holds at the path, with the entity behind it; a version that does not deliver the
 * path, an unknown version and another organization have no entry.
 */
class RenderPathIT extends AbstractVersionIT {

    @Autowired
    private SkillQueryService skillQueryService;

    private UUID base;
    private UUID edited;

    @BeforeEach
    void setUp() {
        initTenant();
        UUID pack = insertPack(tenant.organization(), "paths");
        base = approvedVersion(pack, 1);
        edited = insertDraft(pack, 2, proposer);
        renderService.parse(organizationId(), base, RenderTrees.base());
        renderService.parse(organizationId(), edited, RenderTrees.edited());
    }

    private UUID organizationId() {
        return tenant.organization().getId();
    }

    private static String describe(RenderedFile file) {
        return file == null ? null : String.join("|", file.path(), String.valueOf(file.mode()), String.valueOf(file.symlinkTarget()),
                String.valueOf(file.sha256()), String.valueOf(file.contentBlobId()), String.valueOf(file.overridesPath()),
                String.valueOf(file.entityKind()), String.valueOf(file.entityId()),
                file.content() == null ? "null" : Arrays.toString(file.content()));
    }

    private static Map<String, RenderedFile> byPath(List<RenderedFile> files) {
        return files.stream().collect(Collectors.toMap(RenderedFile::path, file -> file));
    }

    @Test
    void renderPath_everyPathOfTwoVersions_equalsTheEntryOfTheWholeRender() {
        Map<String, RenderedFile> baseFiles = byPath(filesOf(base));
        Map<String, RenderedFile> editedFiles = byPath(filesOf(edited));
        Set<String> paths = new TreeSet<>(baseFiles.keySet());
        paths.addAll(editedFiles.keySet());
        assertTrue(paths.contains(".claude/skills/kaizen"));

        for (String path : paths) {
            Map<UUID, RenderedFile> rendered = renderQueryService.renderPath(organizationId(),
                    List.of(base, edited, UUID.randomUUID()), path);

            assertEquals(describe(baseFiles.get(path)), describe(rendered.get(base)), path);
            assertEquals(describe(editedFiles.get(path)), describe(rendered.get(edited)), path);
            assertEquals((baseFiles.containsKey(path) ? 1 : 0) + (editedFiles.containsKey(path) ? 1 : 0), rendered.size(), path);
        }
    }

    @Test
    void renderPath_pathNoVersionDelivers_isEmpty() {
        assertTrue(renderQueryService.renderPath(organizationId(), List.of(base, edited), ".agents/rules/none.md").isEmpty());
        assertTrue(renderQueryService.renderPath(organizationId(), List.of(base, edited), "src/Main.java").isEmpty());
    }

    @Test
    void renderPath_anotherOrganization_isEmpty() {
        assertTrue(renderQueryService.renderPath(UUID.randomUUID(), List.of(base), ".agents/rules/00-core.md").isEmpty());
    }

    @Test
    void render_everyStoredFile_namesItsEntity() {
        Map<String, RenderedFile> files = byPath(filesOf(base));
        Map<String, UUID> partIds = skillQueryService.getPartLinks(organizationId(), base, "kaizen").stream()
                .collect(Collectors.toMap(link -> ".agents/skills/kaizen/" + link.path(), SkillPartLink::partId));

        for (RenderedFile file : files.values()) {
            if (file.mode() == FileMode.SYMLINK) {
                assertNull(file.entityKind(), file.path());
                assertNull(file.entityId(), file.path());
            } else {
                assertNotNull(file.entityKind(), file.path());
                assertNotNull(file.entityId(), file.path());
            }
        }
        assertEquals(EntityKind.SCRIPT, files.get(".agents/skills/kaizen/scripts/run.sh").entityKind());
        assertEquals(EntityKind.TOOL_PARAMETER, files.get(".agents/skills/kaizen/agents/openai.yml").entityKind());
        assertEquals(EntityKind.SKILL, files.get(".agents/skills/kaizen/SKILL.md").entityKind());
        assertEquals(EntityKind.HOOK, files.get(".agents/hooks/guard.sh").entityKind());
        partIds.forEach((path, partId) -> assertEquals(partId, files.get(path).entityId(), path));
    }
}
