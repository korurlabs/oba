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
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The file rules a draft save enforces one by one, who may write to a draft, and what the diff and
 * the rebase report for added, deleted, mode-changed, linked and binary files.
 */
class DraftRulesIT extends AbstractVersionIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AGENTS = "# Agents\n";
    private static final String LOCAL_RULE = "---\npaths: \"src/**\"\n---\nLocal\n";
    private static final String REASON = "Tighten the agent instructions";

    private UUID packId;
    private UUID v1;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        packId = insertRepositoryPack(tenant.organization(), saveRepository(tenant.organization()).getId(),
                "ws-" + uniqueSuffix().substring(0, 8));
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

    private ResultActions save(UUID draft, String path, String content) throws Exception {
        return saveAs(draft, path, false, content);
    }

    private ResultActions saveAs(UUID draft, String path, boolean executable, String content) throws Exception {
        return mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                body("path", path, "executable", executable, "content", content)));
    }

    private MvcResult diff(UUID version, String against) throws Exception {
        String query = against == null ? "" : "?against=" + against;
        return mockMvc.perform(in(tenant, get("/kaya/versions/" + version + "/diff" + query), member, null))
                .andExpect(status().isOk())
                .andReturn();
    }

    private UUID releaseWith(int number, Map<String, String> files) {
        UUID version = approvedVersion(packId, number);
        for (Map.Entry<String, String> file : files.entrySet()) {
            insertFile(tenant.organization(), version, file.getKey(), file.getValue());
        }
        return version;
    }

    private static String skill(String frontmatter) {
        return "---\n" + frontmatter + "---\nBody\n";
    }

    private void assertRule(ResultActions result, String rule) throws Exception {
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_027"))
                .andExpect(jsonPath("$.error.errorValues.violations[?(@.rule == '" + rule + "')]").exists());
    }

    private void assertNotDeliverable(ResultActions result) throws Exception {
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_028"));
    }

    private void assertInvalidFile(ResultActions result) throws Exception {
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_027"));
    }

    // --- Save rules ---

    @Test
    void saveFile_ruleWithYamlCommentsAndOddKeyOrder_keepsTheBytesAndRefreshesTheIndexOnEverySave() throws Exception {
        // Arrange
        UUID draft = startDraft(packId, owner);
        String first = "---\n# why this rule exists\ndescription:   'Odd  spacing'\npaths:\n  - \"src/**\"   # sources\n---\n\nBody\n";
        String second = "---\npaths: [ 'docs/**', \"lib/**\" ]\n# moved below\ndescription: \"Second\"\n---\nBody two\n";

        // Act and Assert
        save(draft, ".agents/rules/local.md", first).andExpect(status().isOk());
        assertEquals(first, contentOf(draft, ".agents/rules/local.md"));
        assertEquals("Odd  spacing", indexedDescription(draft));
        assertEquals("[\"src/**\"]", indexedPaths(draft));

        save(draft, ".agents/rules/local.md", second).andExpect(status().isOk());
        assertEquals(second, contentOf(draft, ".agents/rules/local.md"));
        assertEquals("Second", indexedDescription(draft));
        assertEquals("[\"docs/**\", \"lib/**\"]", indexedPaths(draft));
        assertEquals(1, count("SELECT count(*) FROM kaya.rules WHERE version_id = ?", draft));
    }

    private String indexedDescription(UUID version) {
        return jdbcTemplate.queryForObject("SELECT description FROM kaya.rules WHERE version_id = ?", String.class, version);
    }

    private String indexedPaths(UUID version) {
        return jdbcTemplate.queryForObject("SELECT paths::text FROM kaya.rules WHERE version_id = ?", String.class, version);
    }

    @Test
    void saveFile_skillWithUnclosedFrontmatter_isRefused() throws Exception {
        UUID draft = startDraft(packId, owner);

        assertInvalidFile(save(draft, ".agents/skills/plain/SKILL.md", "---\nname: plain\ndescription: Never closed\n"));
    }

    @Test
    void saveFile_skillWithUnknownKey_isRefused() throws Exception {
        UUID draft = startDraft(packId, owner);

        assertRule(save(draft, ".agents/skills/odd/SKILL.md",
                skill("name: odd\ndescription: Odd\nsurprise: yes\n")), "FRONTMATTER_KEY_UNKNOWN");
    }

    @Test
    void saveFile_skillListingOverTheLimit_isRefused() throws Exception {
        UUID draft = startDraft(packId, owner);

        assertInvalidFile(save(draft, ".agents/skills/wordy/SKILL.md",
                skill("name: wordy\ndescription: " + "d".repeat(4001) + "\n")));
        assertRule(save(draft, ".agents/skills/wordy/SKILL.md",
                skill("name: wordy\ndescription: " + "d".repeat(1000) + "\nwhen_to_use: " + "w".repeat(537) + "\n")),
                "SKILL_DESCRIPTION_TOO_LONG");
        save(draft, ".agents/skills/wordy/SKILL.md",
                skill("name: wordy\ndescription: " + "d".repeat(1000) + "\nwhen_to_use: " + "w".repeat(536) + "\n"))
                .andExpect(status().isOk());
    }

    @Test
    void saveFile_skillOfFifteenHundredLines_isRefusedAndFourteenNinetyNineSaved() throws Exception {
        UUID draft = startDraft(packId, owner);
        String head = "---\nname: long\ndescription: Long\n---\n";

        assertRule(save(draft, ".agents/skills/long/SKILL.md", head + "x\n".repeat(1496)), "SKILL_TOO_LONG");
        save(draft, ".agents/skills/long/SKILL.md", head + "x\n".repeat(1495)).andExpect(status().isOk());
    }

    @Test
    void saveFile_settingsThatAreNotJson_isRefused() throws Exception {
        UUID draft = startDraft(packId, owner);

        assertInvalidFile(save(draft, ".claude/settings.json", "not json"));
        assertEquals(0, list(diff(draft, null), "$.data[*].path").size());
    }

    @Test
    void saveFile_skillLinkOfAnExistingSkill_isNotDeliverable() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, ".agents/skills/real/SKILL.md", skill("name: real\ndescription: Real\n")).andExpect(status().isOk());

        assertNotDeliverable(save(draft, ".claude/skills/real", "../../.agents/skills/real"));
    }

    @Test
    void saveFile_pathsThatDifferOnlyInCase_areRefused() throws Exception {
        UUID draft = startDraft(packId, owner);

        assertRule(save(draft, ".agents/rules/LOCAL.md", "---\npaths: \"src/**\"\n---\nShout\n"), "PATH_CASE_CONFLICT");
    }

    @Test
    void saveFile_pathBeyondFiveHundredCharacters_answers422() throws Exception {
        UUID draft = startDraft(packId, owner);

        save(draft, ".agents/rules/" + "a".repeat(500) + ".md", "x")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
    }

    @Test
    void saveFile_base64Content_isStoredByteForByte() throws Exception {
        UUID draft = startDraft(packId, owner);
        byte[] bytes = {0, 1, 2, (byte) 0xff, (byte) 0xfe};

        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                        body("path", ".agents/hooks/bin.dat", "encoding", "BASE64",
                                "content", Base64.getEncoder().encodeToString(bytes))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sizeBytes").value(5));

        RenderedFile stored = filesOf(draft).stream()
                .filter(file -> file.path().equals(".agents/hooks/bin.dat"))
                .findFirst()
                .orElseThrow();
        assertTrue(Arrays.equals(bytes, stored.content()));
    }

    @Test
    void saveFile_contentOfExactlyTheSizeLimit_isAccepted() throws Exception {
        UUID draft = startDraft(packId, owner);

        save(draft, ".agents/templates/edge.md", "a".repeat(5_242_880)).andExpect(status().isOk());
    }

    // --- Who may write ---

    @Test
    void draftWrites_byAnotherMember_answer403() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, "AGENTS.md", "# Draft\n").andExpect(status().isOk());

        for (ResultActions attempt : List.of(
                mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft + "/files").param("path", "AGENTS.md"), admin, null)),
                mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft), admin, null)),
                mockMvc.perform(in(tenant, post("/kaya/drafts/" + draft + "/rebase"), admin, null)),
                mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/submit"), admin, body("reason", REASON))))) {
            attempt.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("GEN_SEC_002"));
        }
        assertEquals(2, filesOf(draft).size());
    }

    @Test
    void draftEndpoints_withoutJwt_answer401() throws Exception {
        UUID draft = startDraft(packId, owner);

        mockMvc.perform(post("/kaya/drafts").header("X-Organization-ID", tenant.id())
                        .contentType("application/json").content(draftBody(packId))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/kaya/drafts/" + draft).header("X-Organization-ID", tenant.id())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/kaya/versions/" + draft + "/diff").header("X-Organization-ID", tenant.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void draftWrites_unknownDraft_answer404() throws Exception {
        UUID unknown = UUID.randomUUID();

        save(unknown, "AGENTS.md", "x").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        mockMvc.perform(in(tenant, post("/kaya/changes/" + unknown + "/submit"), owner, body("reason", REASON)))
                .andExpect(status().isNotFound());
        mockMvc.perform(in(tenant, post("/kaya/drafts/" + unknown + "/rebase"), owner, null)).andExpect(status().isNotFound());
    }

    @Test
    void rebase_versionThatIsNotADraft_answers409() throws Exception {
        mockMvc.perform(in(tenant, post("/kaya/drafts/" + v1 + "/rebase"), owner, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_014"));
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + v1 + "/files").param("path", "AGENTS.md"), owner, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_014"));
    }

    // --- Diff ---

    @Test
    void diff_reportsAddedDeletedModifiedAndModeChanges() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, ".agents/templates/new.md", "New\n").andExpect(status().isOk());
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft + "/files").param("path", ".agents/rules/local.md"), owner, null))
                .andExpect(status().isOk());
        save(draft, "AGENTS.md", "# Agents v2\n").andExpect(status().isOk());

        MvcResult result = diff(draft, null);

        assertEquals(List.of(".agents/rules/local.md", ".agents/templates/new.md", "AGENTS.md"), list(result, "$.data[*].path"));
        assertEquals(List.of("DELETED", "ADDED", "MODIFIED"), list(result, "$.data[*].change"));
        assertTrue(read(result, "$.data[1].unifiedDiff").contains("+New\n"));
        assertNull(JSON.readTree(result.getResponse().getContentAsString()).at("/data/0/newSha256").textValue());
    }

    @Test
    void diff_modeOnlyChange_isReportedWithoutContentChange() throws Exception {
        UUID v2 = approvedVersion(packId, 2);
        insertFile(tenant.organization(), v2, ".agents/hooks/run.sh", "#!/bin/sh\nexit 0\n");
        UUID draft = startDraft(packId, owner);
        saveAs(draft, ".agents/hooks/run.sh", true, "#!/bin/sh\nexit 0\n").andExpect(status().isOk());

        MvcResult result = diff(draft, null);

        assertEquals(List.of(".agents/hooks/run.sh"), list(result, "$.data[*].path"));
        assertEquals("FILE", read(result, "$.data[0].oldMode"));
        assertEquals("EXECUTABLE", read(result, "$.data[0].newMode"));
    }

    @Test
    void diff_linkAndBinary_haveNoUnifiedDiff() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, ".agents/skills/real/SKILL.md", skill("name: real\ndescription: Real\n")).andExpect(status().isOk());
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                        body("path", ".agents/hooks/bin.dat", "encoding", "BASE64",
                                "content", Base64.getEncoder().encodeToString(new byte[] {(byte) 0xff, (byte) 0xfe, 0}))))
                .andExpect(status().isOk());

        MvcResult result = diff(draft, null);

        assertEquals(List.of(".agents/hooks/bin.dat", ".agents/skills/real/SKILL.md", ".claude/skills/real"),
                list(result, "$.data[*].path"));
        assertEquals("true", read(result, "$.data[0].binary"));
        assertEquals("../../.agents/skills/real", read(result, "$.data[2].newSymlinkTarget"));
        assertEquals("false", read(result, "$.data[2].binary"));
        assertNull(JSON.readTree(result.getResponse().getContentAsString()).at("/data/0/unifiedDiff").textValue());
        assertNull(JSON.readTree(result.getResponse().getContentAsString()).at("/data/2/unifiedDiff").textValue());
    }

    @Test
    void diff_againstAnotherVersion_comparesTheTwo() throws Exception {
        UUID v2 = releaseWith(2, Map.of("AGENTS.md", "# Agents v2\n", ".agents/rules/local.md", LOCAL_RULE));

        MvcResult againstV1 = diff(v2, v1.toString());
        MvcResult reverse = diff(v1, v2.toString());

        assertEquals(List.of("AGENTS.md"), list(againstV1, "$.data[*].path"));
        assertEquals(List.of("AGENTS.md"), list(reverse, "$.data[*].path"));
        assertTrue(read(againstV1, "$.data[0].unifiedDiff").contains("+# Agents v2\n"));
        assertTrue(read(reverse, "$.data[0].unifiedDiff").contains("-# Agents v2\n"));
    }

    @Test
    void diff_withPath_limitsTheDiffToThatFile() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, ".agents/templates/new.md", "New\n").andExpect(status().isOk());
        save(draft, "AGENTS.md", "# Agents v2\n").andExpect(status().isOk());

        MvcResult changed = mockMvc.perform(in(tenant, get("/kaya/versions/" + draft + "/diff").param("path", "AGENTS.md"),
                        member, null))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult unchanged = mockMvc.perform(in(tenant, get("/kaya/versions/" + draft + "/diff")
                        .param("path", ".agents/rules/local.md"), member, null))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(List.of("AGENTS.md"), list(changed, "$.data[*].path"));
        assertEquals(List.of(), list(unchanged, "$.data[*].path"));
    }

    @Test
    void diff_versionOfAnotherOrganization_answers404() throws Exception {
        Tenant other = newTenant();
        RequestPostProcessor otherOwner = jwtOf(other.owner().getUser());

        mockMvc.perform(in(other, get("/kaya/versions/" + v1 + "/diff"), otherOwner, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    // --- Rebase conflicts of every kind ---

    @Test
    void rebase_draftAddedWhatCurrentAdded_reportsAddedAdded() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, ".agents/templates/t.md", "Mine\n").andExpect(status().isOk());
        releaseWith(3, Map.of("AGENTS.md", AGENTS, ".agents/rules/local.md", LOCAL_RULE, ".agents/templates/t.md", "Theirs\n"));

        mockMvc.perform(in(tenant, post("/kaya/drafts/" + draft + "/rebase"), owner, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].path").value(".agents/templates/t.md"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].draftChange").value("ADDED"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].currentChange").value("ADDED"));
    }

    @Test
    void rebase_draftEditedWhatCurrentDeleted_reportsModifiedDeleted() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, "AGENTS.md", "# Mine\n").andExpect(status().isOk());
        releaseWith(3, Map.of(".agents/rules/local.md", LOCAL_RULE));

        mockMvc.perform(in(tenant, post("/kaya/drafts/" + draft + "/rebase"), owner, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].path").value("AGENTS.md"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].draftChange").value("MODIFIED"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].currentChange").value("DELETED"));
    }

    @Test
    void rebase_twoConflicts_areResolvedOneEachWay() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, "AGENTS.md", "# Mine\n").andExpect(status().isOk());
        save(draft, ".agents/rules/local.md", "---\npaths: \"lib/**\"\n---\nMine\n").andExpect(status().isOk());
        UUID v3 = releaseWith(3, Map.of("AGENTS.md", "# Theirs\n", ".agents/rules/local.md", "---\npaths: \"app/**\"\n---\nTheirs\n"));

        mockMvc.perform(in(tenant, post("/kaya/drafts/" + draft + "/rebase"), owner, body("keepDraft", List.of("AGENTS.md"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.errorValues.conflicts.length()").value(1))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].path").value(".agents/rules/local.md"));
        mockMvc.perform(in(tenant, post("/kaya/drafts/" + draft + "/rebase"), owner,
                        body("keepDraft", List.of("AGENTS.md"), "takeCurrent", List.of(".agents/rules/local.md"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseVersionId").value(v3.toString()));

        assertEquals(List.of("AGENTS.md"), list(diff(draft, null), "$.data[*].path"));
    }
}
