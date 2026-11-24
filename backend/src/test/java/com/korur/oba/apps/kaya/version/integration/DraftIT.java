package com.korur.oba.apps.kaya.version.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drafts of a pack version over HTTP: start, file saves with the size and growth limits, file
 * and draft deletes, the diff and the rebase of a draft onto a newer released version.
 */
class DraftIT extends AbstractVersionIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AGENTS = "# Agents\n";
    private static final String LOCAL_RULE = "---\npaths: \"src/**\"\n---\nLocal rule\n";
    private static final String VERSION_COUNT = "SELECT count(*) FROM kaya.versions WHERE pack_id = ?";

    private Repository repository;
    private UUID packId;
    private UUID v1;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        repository = saveRepository(tenant.organization());
        packId = insertRepositoryPack(tenant.organization(), repository.getId(), "ws-" + uniqueSuffix().substring(0, 8));
        v1 = approvedVersion(packId, 1);
        insertFile(tenant.organization(), v1, "AGENTS.md", AGENTS);
        insertFile(tenant.organization(), v1, ".agents/rules/local.md", LOCAL_RULE);
    }

    // --- Helpers ---

    private static String body(Object... keysAndValues) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return JSON.writeValueAsString(map);
    }

    private ResultActions save(RequestPostProcessor jwt, UUID draft, String path, String content) throws Exception {
        return mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), jwt,
                body("path", path, "content", content)));
    }

    private ResultActions save(UUID draft, String path, String content) throws Exception {
        return save(owner, draft, path, content);
    }

    private ResultActions saveExecutable(UUID draft, String path, String content) throws Exception {
        return mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                body("path", path, "executable", true, "content", content)));
    }

    private ResultActions submit(UUID draft) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/submit"), owner,
                body("reason", "Tighten the agent instructions")));
    }

    private ResultActions rebase(UUID draft, String requestBody) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/drafts/" + draft + "/rebase"), owner, requestBody));
    }

    private MvcResult diff(UUID version) throws Exception {
        return mockMvc.perform(in(tenant, get("/kaya/versions/" + version + "/diff"), member, null))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** A newer released version: a copy of v1 with the given files replaced. */
    private UUID release(int number, String... pathsAndContents) {
        UUID version = approvedVersion(packId, number);
        Map<String, String> files = new LinkedHashMap<>(Map.of("AGENTS.md", AGENTS, ".agents/rules/local.md", LOCAL_RULE));
        for (int index = 0; index + 1 < pathsAndContents.length; index += 2) {
            files.put(pathsAndContents[index], pathsAndContents[index + 1]);
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            insertFile(tenant.organization(), version, file.getKey(), file.getValue());
        }
        return version;
    }

    private static String skill(int bodyLines) {
        StringBuilder skill = new StringBuilder("---\nname: big\ndescription: A long skill\n---\n");
        for (int line = 0; line < bodyLines; line++) {
            skill.append("line ").append(line).append('\n');
        }
        return skill.toString();
    }

    // --- Create ---

    @Test
    void createDraft_ownerWithCurrentVersion_copiesTheFilesAndAnswers201() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post("/kaya/drafts"), member, draftBody(packId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.baseVersionId").value(v1.toString()))
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.proposedBy").value(tenant.member().getUser().getId().toString()))
                .andReturn();
        UUID draft = UUID.fromString(read(result, "$.data.id"));

        assertEquals(2, filesOf(draft).size());
        assertEquals(v1, currentVersionOf(packId));
        assertEquals(0, list(diff(draft), "$.data[*].path").size());
    }

    @Test
    void createDraft_packOfAnotherOrganization_answers404() throws Exception {
        Tenant other = newTenant();
        UUID foreign = insertPack(other.organization(), "foreign");

        mockMvc.perform(in(tenant, post("/kaya/drafts"), owner, draftBody(foreign)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    @Test
    void createDraft_ownerWithoutVersion_startsWithoutBase() throws Exception {
        UUID pack = insertPack(tenant.organization(), "empty");

        mockMvc.perform(in(tenant, post("/kaya/drafts"), owner, draftBody(pack)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.baseVersionId").doesNotExist());
    }

    @Test
    void createDraft_numbersFollowTheHighestAndAreNotReused() throws Exception {
        UUID first = startDraft(packId, owner);
        UUID second = startDraft(packId, owner);
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + first), owner, null)).andExpect(status().isOk());

        UUID third = startDraft(packId, owner);

        assertEquals(3, jdbcTemplate.queryForObject("SELECT number FROM kaya.versions WHERE id = ?", Integer.class, second));
        assertEquals(4, jdbcTemplate.queryForObject("SELECT number FROM kaya.versions WHERE id = ?", Integer.class, third));
    }

    // --- Save, delete and diff ---

    @Test
    void saveAndDeleteFiles_byTheProposer_showInTheDiff() throws Exception {
        UUID draft = startDraft(packId, owner);

        save(draft, "AGENTS.md", "# Changed\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value("AGENTS.md"))
                .andExpect(jsonPath("$.data.kind").value("AGENTS_FILE"))
                .andExpect(jsonPath("$.data.executable").value(false))
                .andExpect(jsonPath("$.data.sizeBytes").value(10));
        String encoded = Base64.getEncoder().encodeToString("T\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                        body("path", ".agents/templates/T.md", "encoding", "BASE64", "content", encoded)))
                .andExpect(status().isOk());
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft + "/files").param("path", ".agents/rules/local.md"),
                        owner, null))
                .andExpect(status().isOk());

        MvcResult result = diff(draft);
        assertEquals(List.of(".agents/rules/local.md", ".agents/templates/T.md", "AGENTS.md"), list(result, "$.data[*].path"));
        assertEquals(List.of("DELETED", "ADDED", "MODIFIED"), list(result, "$.data[*].change"));
        assertEquals("--- a/AGENTS.md\n+++ b/AGENTS.md\n@@ -1,1 +1,1 @@\n-# Agents\n+# Changed\n", read(result, "$.data[2].unifiedDiff"));
        assertEquals("--- /dev/null\n+++ b/.agents/templates/T.md\n@@ -0,0 +1,1 @@\n+T\n", read(result, "$.data[1].unifiedDiff"));
        assertEquals(2, filesOf(draft).size());
    }

    @Test
    void saveFile_notTheProposer_answers403() throws Exception {
        UUID draft = startDraft(packId, owner);

        save(member, draft, "AGENTS.md", "# Member\n")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GEN_SEC_002"));
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft), member, null))
                .andExpect(status().isForbidden());
        assertEquals(0, list(diff(draft), "$.data[*].path").size());
    }

    @Test
    void saveFile_versionOfAnotherOrganization_answers404() throws Exception {
        Tenant other = newTenant();
        UUID foreignPack = insertPack(other.organization(), "foreign");
        UUID foreignDraft = insertDraft(foreignPack, 1, proposer);

        save(foreignDraft, "AGENTS.md", "# X\n")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    @Test
    void saveFile_badRequests_answer422() throws Exception {
        UUID draft = startDraft(packId, owner);

        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                        body("path", "AGENTS.md", "encoding", "BASE64", "content", "not base64!")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner, body("path", "AGENTS.md")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        save(draft, "src/Main.java", "class Main {}\n")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_028"))
                .andExpect(jsonPath("$.error.errorValues.path").value("src/Main.java"));
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft + "/files").param("path", "missing.md"), owner, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    @Test
    void saveFile_skillLinkOrFolderPath_isNotDeliverable() throws Exception {
        UUID draft = startDraft(packId, owner);

        save(draft, ".claude/skills/missing", "../../.agents/skills/missing")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_028"));
        save(draft, ".agents/rules", "text")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_028"));
        assertEquals(0, list(diff(draft), "$.data[*].path").size());
    }

    @Test
    void saveFile_rule_answersItsEntityAndHashWithoutTheContent() throws Exception {
        UUID draft = startDraft(packId, owner);

        MvcResult result = save(draft, ".agents/rules/saved.md", "# Saved\n").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value(".agents/rules/saved.md"))
                .andExpect(jsonPath("$.data.kind").value("RULE"))
                .andExpect(jsonPath("$.data.sizeBytes").value(8))
                .andExpect(jsonPath("$.data.executable").value(false))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andReturn();

        MvcResult rules = mockMvc.perform(in(tenant, get("/kaya/rules?versionId=" + draft), owner, null))
                .andExpect(status().isOk()).andReturn();
        assertTrue(list(rules, "$.data[*].id").contains(read(result, "$.data.entityId")));
        RenderedFile stored = filesOf(draft).stream().filter(file -> file.path().equals(".agents/rules/saved.md")).findFirst()
                .orElseThrow();
        assertEquals(stored.sha256(), read(result, "$.data.sha256"));
    }

    @Test
    void saveFile_executableHookOrSkillFile_isStoredExecutable() throws Exception {
        UUID draft = startDraft(packId, owner);

        saveExecutable(draft, ".agents/hooks/run.sh", "#!/bin/sh\nexit 0\n").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("HOOK"))
                .andExpect(jsonPath("$.data.executable").value(true));
        save(draft, ".agents/skills/tool/SKILL.md", "---\nname: tool\ndescription: A tool\n---\n# Tool\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("SKILL"));
        saveExecutable(draft, ".agents/skills/tool/bin/run", "#!/bin/sh\n").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("SCRIPT"))
                .andExpect(jsonPath("$.data.executable").value(true));
    }

    @Test
    void saveFile_executableOfAnotherKind_isInvalidFile() throws Exception {
        UUID draft = startDraft(packId, owner);

        saveExecutable(draft, ".agents/rules/run.md", "# Run\n")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_027"));
        saveExecutable(draft, ".agents/skills/tool/SKILL.md", "---\nname: tool\ndescription: A tool\n---\n")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_027"));
        assertEquals(0, list(diff(draft), "$.data[*].path").size());
    }

    @Test
    void saveFile_newFileOverTheSizeLimit_answers422() throws Exception {
        UUID draft = startDraft(packId, owner);

        save(draft, ".agents/templates/big.md", "a".repeat(5_242_881))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_027"))
                .andExpect(jsonPath("$.error.errorValues.violations[0].rule").value("FILE_TOO_LARGE"));
    }

    @Test
    void saveFile_seededSkillOverTheLimit_mayShrinkButNotGrow() throws Exception {
        UUID v2 = approvedVersion(packId, 2);
        insertFile(tenant.organization(), v2, "AGENTS.md", AGENTS);
        insertSeededFile(tenant.organization(), v2, ".agents/skills/big/SKILL.md", skill(1600));
        UUID draft = startDraft(packId, owner);

        save(draft, "AGENTS.md", "# Other\n").andExpect(status().isOk());
        save(draft, ".agents/skills/big/SKILL.md", skill(1599)).andExpect(status().isOk());
        save(draft, ".agents/skills/big/SKILL.md", skill(1601)).andExpect(status().isUnprocessableEntity());
        save(draft, ".agents/skills/other/SKILL.md", skill(1600).replace("name: big", "name: other"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void saveFile_seededSkillOverTheLineLimit_mayGrowInBytesWithoutGainingLines() throws Exception {
        UUID v2 = approvedVersion(packId, 2);
        insertFile(tenant.organization(), v2, "AGENTS.md", AGENTS);
        insertSeededFile(tenant.organization(), v2, ".agents/skills/big/SKILL.md", skill(1600));
        UUID draft = startDraft(packId, owner);

        save(draft, ".agents/skills/big/SKILL.md", skill(1600).replace("line 0\n", "line 0 with a longer wording\n"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteDraft_byTheProposer_removesItWithItsFiles() throws Exception {
        UUID draft = startDraft(packId, owner);

        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft), owner, null)).andExpect(status().isOk());

        assertEquals(0, count("SELECT count(*) FROM kaya.versions WHERE id = ?", draft));
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft), owner, null)).andExpect(status().isNotFound());
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + v1), owner, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_014"));
        assertEquals(1, count(VERSION_COUNT, packId));
    }

    // --- Rebase ---

    @Test
    void rebase_baseIsCurrent_returnsTheDraftUnchanged() throws Exception {
        UUID draft = startDraft(packId, owner);

        rebase(draft, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseVersionId").value(v1.toString()));
    }

    @Test
    void rebase_noConflict_takesTheCurrentChangesAndKeepsTheDraftOnes() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, "AGENTS.md", "# Draft\n").andExpect(status().isOk());
        UUID v3 = release(3, ".agents/rules/local.md", LOCAL_RULE + "More\n", ".agents/templates/new.md", "New\n");

        submit(draft)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_002"))
                .andExpect(jsonPath("$.error.errorValues.currentVersionId").value(v3.toString()));
        rebase(draft, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseVersionId").value(v3.toString()));

        MvcResult result = diff(draft);
        assertEquals(List.of("AGENTS.md"), list(result, "$.data[*].path"));
        assertEquals(3, filesOf(draft).size());
        assertEquals(LOCAL_RULE + "More\n", contentOf(draft, ".agents/rules/local.md"));
    }

    @Test
    void rebase_conflict_answers409UntilResolved() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, "AGENTS.md", "# Draft\n").andExpect(status().isOk());
        UUID v3 = release(3, "AGENTS.md", "# Current\n");

        rebase(draft, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_002"))
                .andExpect(jsonPath("$.error.errorValues.currentVersionId").value(v3.toString()))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].path").value("AGENTS.md"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].draftChange").value("MODIFIED"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].currentChange").value("MODIFIED"));
        rebase(draft, body("keepDraft", List.of("AGENTS.md"), "takeCurrent", List.of("AGENTS.md")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));

        rebase(draft, body("keepDraft", List.of("AGENTS.md")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseVersionId").value(v3.toString()));
        assertTrue(read(diff(draft), "$.data[0].unifiedDiff").contains("-# Current\n+# Draft\n"));
    }

    @Test
    void rebase_conflictTakenFromCurrent_leavesNoChange() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, "AGENTS.md", "# Draft\n").andExpect(status().isOk());
        release(3, "AGENTS.md", "# Current\n");

        rebase(draft, body("takeCurrent", List.of("AGENTS.md"))).andExpect(status().isOk());

        assertEquals(0, list(diff(draft), "$.data[*].path").size());
        submit(draft)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_016"));
    }
}
