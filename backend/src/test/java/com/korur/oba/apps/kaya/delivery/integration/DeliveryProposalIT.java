package com.korur.oba.apps.kaya.delivery.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proposals from the CLI: files edited on a checkout's release become a CLI version in review in
 * one call, owned by the set that delivered them, refused whole when the set moved on under a
 * proposed path or when any config rule breaks, and approved like any other change.
 */
class DeliveryProposalIT extends AbstractVersionIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AGENTS = "# Agents\n";
    private static final String HOOK = ".agents/hooks/check.sh";
    private static final String STYLE = "---\npaths: \"src/**\"\n---\nStyle\n";
    private static final String REASON = "Sharpen the agent instructions";
    private static final String VERSIONS_OF_ROOT_PACK = "SELECT count(*) FROM kaya.versions WHERE pack_id = ?";

    private Repository repository;
    private String remote;
    private UUID rootPack;
    private UUID v1;
    private UUID pack;
    private Person developer;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        repository = saveRepository(tenant.organization());
        remote = "git@" + repository.getRemoteUrl().replaceFirst("/", ":") + ".git";
        rootPack = insertRepositoryPack(tenant.organization(), repository.getId(), "root-" + uniqueSuffix().substring(0, 8));
        v1 = approvedVersion(rootPack, 1);
        insertFile(tenant.organization(), v1, "AGENTS.md", AGENTS);
        insertFile(tenant.organization(), v1, HOOK, FileMode.EXECUTABLE, "#!/bin/sh\n");
        insertSeededFile(tenant.organization(), v1, ".agents/skills/big/SKILL.md", skill(1600));
        pack = packWith("org", ".agents/rules/style.md", STYLE);
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack, rootPack))).andExpect(status().isOk());

        developer = signIn(tenant, "dev");
        token = machineToken(developer.jwt(), null);
    }

    // --- Helpers ---

    private String machineToken(RequestPostProcessor jwt, String userId)
            throws Exception {
        String body = userId == null
                ? "{\"machineName\":\"m-" + uniqueSuffix() + "\"}"
                : "{\"machineName\":\"m-" + uniqueSuffix() + "\",\"userId\":\"" + userId + "\"}";
        MvcResult created = mockMvc.perform(in(tenant, post("/kaya/machines"), jwt, body))
                .andExpect(status().isCreated())
                .andReturn();
        return read(created, "$.data.token");
    }

    private static String skill(int bodyLines) {
        StringBuilder skill = new StringBuilder("---\nname: big\ndescription: A long skill\n---\n");
        for (int line = 0; line < bodyLines; line++) {
            skill.append("line ").append(line).append('\n');
        }
        return skill.toString();
    }

    private static Map<String, Object> file(String path, String content) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("path", path);
        file.put("mode", "FILE");
        file.put("content", content);
        return file;
    }

    private static Map<String, Object> removal(String path) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("path", path);
        file.put("deleted", true);
        return file;
    }

    @SafeVarargs
    private ResultActions propose(String machineToken, int releaseNumber, Map<String, Object>... files) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("remoteUrl", remote);
        body.put("releaseNumber", releaseNumber);
        body.put("reason", REASON);
        body.put("files", List.of(files));
        return proposeBody(machineToken, JSON.writeValueAsString(body));
    }

    private ResultActions proposeBody(String machineToken, String body) throws Exception {
        return mockMvc.perform(post("/kaya/delivery/proposals")
                .header("Authorization", "Bearer " + machineToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID currentVersion(UUID ownerId) {
        return currentVersionOf(ownerId);
    }

    /** A new approved, current version of the root pack: a copy of v1 with one more file. */
    private UUID moveRootPackOn(String path, String content) throws Exception {
        UUID v2 = approvedVersion(rootPack, 2);
        copyFiles(v1, v2, path);
        insertFile(tenant.organization(), v2, path, content);
        return v2;
    }

    /**
     * A child of the org pack, composed at the root beside the root pack and released as release 2:
     * the repository's single deepest root pack.
     */
    private UUID releaseWithAChildPack() throws Exception {
        UUID child = insertChildPack(tenant.organization(), "child-" + uniqueSuffix().substring(0, 8), pack);
        insertFiles(tenant.organization(), approvedVersion(child, 1), ".agents/rules/child.md", STYLE);
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(child, rootPack))).andExpect(status().isOk());
        return child;
    }

    /** A pack named kagi mounted at projects/kagi beside the root packs, released as release 2. */
    private UUID releaseWithAMountedPack() throws Exception {
        UUID kagi = packWith("kagi", "AGENTS.md", "# Kagi\n");
        String body = "{\"packs\":[{\"packId\":\"" + pack + "\"},{\"packId\":\"" + rootPack + "\"},{\"packId\":\"" + kagi
                + "\",\"rootPath\":\"projects/kagi\"}]}";
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, body)).andExpect(status().isOk());
        return kagi;
    }

    // --- Created and submitted ---

    @Test
    void propose_deliveredRootPackFile_createsACliVersionInReviewWithItsReviewUrl() throws Exception {
        MvcResult result = propose(token, 1, file("AGENTS.md", "# Agents v2\n"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.packId").value(rootPack.toString()))
                .andExpect(jsonPath("$.data.projectId").doesNotExist())
                .andExpect(jsonPath("$.data.baseVersionId").value(v1.toString()))
                .andExpect(jsonPath("$.data.releaseNumber").value(1))
                .andExpect(jsonPath("$.data.executableChange").value(false))
                .andReturn();

        String proposalId = read(result, "$.data.proposalId");
        assertEquals("https://kaya.korur.com/changes/" + proposalId, read(result, "$.data.reviewUrl"));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT source::text AS source, proposed_by, reason, submitted_at FROM kaya.versions WHERE id = ?::uuid",
                proposalId);
        assertEquals("CLI", row.get("source"));
        assertEquals(developer.user().getId(), row.get("proposed_by"));
        assertEquals(REASON, row.get("reason"));
        assertTrue(row.get("submitted_at") != null);
        // The draft is a copy of the base with the proposed path changed: AGENTS.md, the hook, the
        // skill and the link the skill renders.
        assertEquals(4, filesOf(UUID.fromString(proposalId)).size());
    }

    @Test
    void propose_approvedByAnAdmin_releasesTheRepository() throws Exception {
        String proposalId = read(propose(token, 1, file("AGENTS.md", "# Agents v2\n")).andExpect(status().isCreated()).andReturn(),
                "$.data.proposalId");

        mockMvc.perform(in(tenant, post("/kaya/changes/" + proposalId + "/reviews"), admin, "{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.releases[0].number").value(2));
        assertEquals(UUID.fromString(proposalId), currentVersion(rootPack));
    }

    @Test
    void propose_proposerApprovesTheirOwnProposal_answers403() throws Exception {
        String proposalId = read(propose(token, 1, file("AGENTS.md", "# Agents v2\n")).andExpect(status().isCreated()).andReturn(),
                "$.data.proposalId");
        jdbcTemplate.update("UPDATE shrine.organization_members SET role = 'OWNER'::shrine_organization_member_role_enum "
                + "WHERE user_id = ?", developer.user().getId());

        mockMvc.perform(in(tenant, post("/kaya/changes/" + proposalId + "/reviews"), developer.jwt(),
                        "{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_004"));
    }

    @Test
    void propose_newPathWithTwoRootPacksOfTheSameDepth_answers422WithBothAsCandidates() throws Exception {
        propose(token, 1, file(".agents/rules/new.md", "---\npaths: \"docs/**\"\n---\nNew\n"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_019"))
                .andExpect(jsonPath("$.error.errorValues.candidates.length()").value(2))
                .andExpect(jsonPath("$.error.errorValues.candidates[?(@ == '" + pack + "')]").exists())
                .andExpect(jsonPath("$.error.errorValues.candidates[?(@ == '" + rootPack + "')]").exists());
        assertEquals(1, count(VERSIONS_OF_ROOT_PACK, rootPack));
    }

    @Test
    void propose_newPath_goesToTheDeepestRootPack() throws Exception {
        UUID child = releaseWithAChildPack();

        propose(token, 2, file(".agents/rules/new.md", "---\npaths: \"docs/**\"\n---\nNew\n"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.packId").value(child.toString()));
    }

    @Test
    void propose_newPathTheCurrentVersionAddedSinceTheRelease_answers409WithAnAddedConflict() throws Exception {
        UUID child = releaseWithAChildPack();
        UUID v2 = approvedVersion(child, 2);
        insertFiles(tenant.organization(), v2, ".agents/rules/child.md", STYLE, ".agents/rules/new.md", "---\npaths: \"docs/**\"\n---\nKaya\n");

        propose(token, 2, file(".agents/rules/new.md", "---\npaths: \"docs/**\"\n---\nNew\n"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_002"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].path").value(".agents/rules/new.md"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].draftChange").value("ADDED"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].currentChange").value("ADDED"));
        assertEquals(2, count(VERSIONS_OF_ROOT_PACK, child));
    }

    @Test
    void propose_newPathNoFileKindAccepts_answers422AndRollsBack() throws Exception {
        UUID child = releaseWithAChildPack();

        propose(token, 2, file("src/main.md", "Not config\n"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_028"));
        assertEquals(1, count(VERSIONS_OF_ROOT_PACK, child));
    }

    @Test
    void propose_newPathInAMountedPacksNamespace_goesToTheMountedPack() throws Exception {
        UUID kagi = releaseWithAMountedPack();

        propose(token, 2, file(".agents/rules/kagi/new.md", "---\npaths: \"projects/kagi/**\"\n---\nNew\n"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.packId").value(kagi.toString()));
    }

    @Test
    void propose_agentsFileOfAMountFolder_isStoredAsThePacksAgentsFile() throws Exception {
        UUID kagi = releaseWithAMountedPack();

        String proposalId = read(propose(token, 2, file("projects/kagi/AGENTS.md", "# Kagi v2\n"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.packId").value(kagi.toString()))
                .andReturn(), "$.data.proposalId");

        assertEquals("# Kagi v2\n", contentOf(UUID.fromString(proposalId), "AGENTS.md"));
        assertEquals(List.of("AGENTS.md"), filesOf(UUID.fromString(proposalId)).stream().map(RenderedFile::path).toList());
    }

    @Test
    void propose_packPath_goesToThePack() throws Exception {
        propose(token, 1, file(".agents/rules/style.md", "---\npaths: \"src/**\"\n---\nStyle v2\n"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.packId").value(pack.toString()));
    }

    @Test
    void propose_removal_dropsThePath() throws Exception {
        String proposalId = read(propose(token, 1, removal(".agents/rules/style.md")).andExpect(status().isCreated()).andReturn(),
                "$.data.proposalId");

        assertEquals(0, filesOf(UUID.fromString(proposalId)).size());
    }

    @Test
    void propose_changedExecutableFile_isAnExecutableChange() throws Exception {
        Map<String, Object> hook = file(HOOK, Base64.getEncoder().encodeToString("#!/bin/sh\nexit 0\n".getBytes()));
        hook.put("mode", "EXECUTABLE");
        hook.put("encoding", "BASE64");

        propose(token, 1, hook)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.executableChange").value(true));
    }

    @Test
    void propose_setMovedOnElsewhere_isBasedOnTheCurrentVersion() throws Exception {
        UUID v2 = moveRootPackOn(".agents/rules/other.md", "---\npaths: \"x/**\"\n---\nOther\n");

        propose(token, 1, file("AGENTS.md", "# Agents v2\n"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.baseVersionId").value(v2.toString()));
    }

    // --- Refused ---

    @Test
    void propose_currentChangedAProposedPathSinceTheRelease_answers409WithTheConflict() throws Exception {
        UUID v2 = moveRootPackOn("AGENTS.md", "# Agents changed in Kaya\n");

        propose(token, 1, file("AGENTS.md", "# Agents v2\n"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_002"))
                .andExpect(jsonPath("$.error.errorValues.currentVersionId").value(v2.toString()))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].path").value("AGENTS.md"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].draftChange").value("MODIFIED"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].currentChange").value("MODIFIED"));
        assertEquals(2, count(VERSIONS_OF_ROOT_PACK, rootPack));
    }

    @Test
    void propose_pathsOfTwoOwners_answers422AndCreatesNothing() throws Exception {
        propose(token, 1, file("AGENTS.md", "# Agents v2\n"), file(".agents/rules/style.md", STYLE + "More\n"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_019"))
                .andExpect(jsonPath("$.error.errorValues.owners['" + rootPack + "'][0]").value("AGENTS.md"))
                .andExpect(jsonPath("$.error.errorValues.owners['" + pack + "'][0]").value(".agents/rules/style.md"));
        assertEquals(1, count(VERSIONS_OF_ROOT_PACK, rootPack));
        assertEquals(1, count("SELECT count(*) FROM kaya.versions WHERE pack_id = ?", pack));
    }

    @Test
    void propose_unchangedFiles_answers409() throws Exception {
        propose(token, 1, file("AGENTS.md", AGENTS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_016"));
        assertEquals(1, count(VERSIONS_OF_ROOT_PACK, rootPack));
    }

    @Test
    void propose_whileAnotherVersionIsInReview_answers409() throws Exception {
        String first = read(propose(token, 1, file("AGENTS.md", "# Agents v2\n")).andExpect(status().isCreated()).andReturn(),
                "$.data.proposalId");

        propose(token, 1, file("AGENTS.md", "# Agents v3\n"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_015"))
                .andExpect(jsonPath("$.error.errorValues.inReviewVersionId").value(first));
    }

    @Test
    void propose_skillTheBaseAlreadyHadOverTheLimit_mayShrinkButNotGrow() throws Exception {
        propose(token, 1, file(".agents/skills/big/SKILL.md", skill(1601)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.errorValues.violations[0].rule").value("SKILL_TOO_LONG"));
        propose(token, 1, file(".agents/skills/big/SKILL.md", skill(1599))).andExpect(status().isCreated());
    }

    @Test
    void propose_aPathTwice_answers422() throws Exception {
        propose(token, 1, file("AGENTS.md", "# A\n"), file("AGENTS.md", "# B\n"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"))
                .andExpect(jsonPath("$.error.errorValues.paths[0]").value("AGENTS.md"));
    }

    @Test
    void propose_withoutModeOrWithBadBase64_answers422() throws Exception {
        Map<String, Object> noMode = file("AGENTS.md", "# A\n");
        noMode.remove("mode");
        propose(token, 1, noMode)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.errorValues.mode").exists());

        Map<String, Object> badBase64 = file("AGENTS.md", "not base64!");
        badBase64.put("encoding", "BASE64");
        propose(token, 1, badBase64)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.errorValues.content").exists());
    }

    @Test
    void propose_invalidBody_answers422() throws Exception {
        proposeBody(token, "{\"remoteUrl\":\"" + remote + "\",\"releaseNumber\":1,\"reason\":\"short\",\"files\":[]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));

        List<Map<String, Object>> many = new ArrayList<>();
        for (int index = 0; index < 201; index++) {
            many.add(file(".agents/rules/r" + index + ".md", "x\n"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("remoteUrl", remote);
        body.put("releaseNumber", 1);
        body.put("reason", REASON);
        body.put("files", many);
        proposeBody(token, JSON.writeValueAsString(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
    }

    @Test
    void propose_unknownReleaseNumber_answers404() throws Exception {
        propose(token, 9, file("AGENTS.md", "# Agents v2\n"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    @Test
    void propose_membershipDisabled_answers403() throws Exception {
        jdbcTemplate.update("UPDATE shrine.organization_members SET status = 'DISABLED'::shrine_organization_member_status_enum "
                + "WHERE user_id = ?", developer.user().getId());

        propose(token, 1, file("AGENTS.md", "# Agents v2\n"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KAY_SEC_002"));
    }

    @Test
    void propose_userDisabled_answers403AndStoresNoVersion() throws Exception {
        jdbcTemplate.update("UPDATE shrine.users SET status = 'DISABLED'::shrine_identity_user_status_enum WHERE id = ?",
                developer.user().getId());

        propose(token, 1, file("AGENTS.md", "# Agents v2\n"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GEN_SEC_002"))
                .andExpect(jsonPath("$.error.errorValues.userStatus").value("DISABLED"));
        assertEquals(0L, count("SELECT count(*) FROM kaya.versions WHERE proposed_by = ?", developer.user().getId()));
    }

    @Test
    void propose_withoutToken_answers401() throws Exception {
        mockMvc.perform(post("/kaya/delivery/proposals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("KAY_SEC_003"));
    }
}
