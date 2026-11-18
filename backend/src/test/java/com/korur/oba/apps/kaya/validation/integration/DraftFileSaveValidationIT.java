package com.korur.oba.apps.kaya.validation.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The save rule of a draft file over HTTP: one transaction parses the file, validates it and
 * indexes it, and a file that fails answers 422 without touching what the draft already stores,
 * neither the files nor the rows indexed from them.
 */
class DraftFileSaveValidationIT extends AbstractVersionIT {

    private static final String CORE_RULE = ".agents/rules/core.md";
    private static final String CORE_CONTENT = "---\ndescription: Core\npaths: \"src/**\"\n---\nCore rule\n";

    private UUID draftId;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        UUID pack = packWith("base", "AGENTS.md", "# Agents\n", CORE_RULE, CORE_CONTENT);
        draftId = startDraft(pack, owner);
    }

    private static String body(String path, String content) {
        return "{\"path\":\"" + path + "\",\"mode\":\"FILE\",\"content\":\"" + content.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
    }

    private ResultActions save(String path, String content) throws Exception {
        return mockMvc.perform(in(tenant, put("/kaya/drafts/" + draftId + "/files"), owner, body(path, content)));
    }

    /** Path and hash of every stored file, to prove a refused save changed nothing. */
    private List<String> snapshot() {
        return filesOf(draftId).stream().map(file -> file.path() + ":" + file.sha256()).toList();
    }

    private long indexedRules() {
        return count("SELECT count(*) FROM kaya.rules WHERE version_id = ?", draftId);
    }

    @Test
    void saveFile_validRule_storesAndIndexesTheFileInTheSameSave() throws Exception {
        List<String> before = snapshot();

        save(".agents/rules/extra.md", "---\ndescription: Extra\npaths: \"docs/**\"\n---\nExtra rule\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value(".agents/rules/extra.md"));

        assertEquals(before.size() + 1, snapshot().size());
        assertEquals("---\ndescription: Extra\npaths: \"docs/**\"\n---\nExtra rule\n", contentOf(draftId, ".agents/rules/extra.md"));
        assertEquals(2, indexedRules());
        assertEquals("Extra", jdbcTemplate.queryForObject(
                "SELECT description FROM kaya.rules WHERE version_id = ? AND relative_path = 'extra.md'", String.class, draftId));
    }

    @Test
    void saveFile_ruleWithBrokenFrontmatter_answers422InvalidFileAndLeavesTheStoredFileUnchanged() throws Exception {
        List<String> before = snapshot();

        save(CORE_RULE, "---\npaths: [unclosed\n---\nBroken\n")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_027"))
                .andExpect(jsonPath("$.error.errorValues.path").value(CORE_RULE));

        assertEquals(before, snapshot());
        assertEquals(CORE_CONTENT, contentOf(draftId, CORE_RULE));
        assertEquals(1, indexedRules());
        assertEquals("Core", jdbcTemplate.queryForObject(
                "SELECT description FROM kaya.rules WHERE version_id = ? AND relative_path = 'core.md'", String.class, draftId));
    }

    @Test
    void saveFile_subagentWithZeroMaxTurns_answers422InvalidFileAndStoresNothing() throws Exception {
        List<String> before = snapshot();

        save(".claude/agents/reviewer.md", "---\nname: reviewer\nmaxTurns: 0\n---\nBody\n")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_027"));

        assertEquals(before, snapshot());
        assertEquals(0, count("SELECT count(*) FROM kaya.subagents WHERE version_id = ?", draftId));
        assertNull(contentOf(draftId, ".claude/agents/reviewer.md"));
    }

    @Test
    void saveFile_settingsThatAreNotJson_answers422InvalidFileAndLeavesNoSettings() throws Exception {
        List<String> before = snapshot();

        save(".claude/settings.json", "{not json")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_027"));

        assertEquals(before, snapshot());
        assertEquals(0, count("SELECT count(*) FROM kaya.settings WHERE version_id = ?", draftId));
    }

    @Test
    void saveFile_pathNoKindDelivers_answers422NotDeliverableAndStoresNothing() throws Exception {
        List<String> before = snapshot();

        save("src/main.md", "Not config\n")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_028"))
                .andExpect(jsonPath("$.error.errorValues.path").value("src/main.md"));

        assertEquals(before, snapshot());
    }

    @Test
    void saveFile_refusedSaveBetweenTwoGoodOnes_leavesTheGoodOnesStored() throws Exception {
        save(".agents/rules/one.md", "---\npaths: \"a/**\"\n---\nOne\n").andExpect(status().isOk());
        save(".agents/rules/bad.md", "---\npaths: [unclosed\n---\nBad\n").andExpect(status().isUnprocessableEntity());
        save(".agents/rules/two.md", "---\npaths: \"b/**\"\n---\nTwo\n").andExpect(status().isOk());

        List<String> paths = filesOf(draftId).stream().map(RenderedFile::path).toList();
        assertEquals(List.of(".agents/rules/core.md", ".agents/rules/one.md", ".agents/rules/two.md", "AGENTS.md"), paths);
        assertEquals(3, indexedRules());
    }
}
