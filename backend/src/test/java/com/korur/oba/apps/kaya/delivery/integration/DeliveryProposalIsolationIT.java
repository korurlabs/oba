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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proposals over the machine token across organizations and against a set that moved on: another
 * organization's token reaches nothing, each kind of conflict is reported with both changes, an
 * executable proposal still needs an owner, and an approved proposal releases the files that were
 * proposed.
 */
class DeliveryProposalIsolationIT extends AbstractVersionIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AGENTS = "# Agents\n";
    private static final String HOOK = ".agents/hooks/check.sh";
    private static final String REASON = "Sharpen the agent instructions";
    private static final String VERSIONS_OF_ROOT_PACK = "SELECT count(*) FROM kaya.versions WHERE pack_id = ?";

    private Repository repository;
    private String remote;
    private UUID rootPack;
    private UUID v1;
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
        insertFile(tenant.organization(), v1, ".agents/rules/local.md", "---\npaths: \"src/**\"\n---\nLocal\n");
        UUID pack = packWith("org", ".agents/rules/style.md", "---\npaths: \"src/**\"\n---\nStyle\n");
        mockMvc.perform(in(tenant, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(packsPath(repository)), owner, packIds(pack, rootPack))).andExpect(status().isOk());
        token = machineTokenOf(tenant, "dev");
    }

    // --- Helpers ---

    private String machineTokenOf(Tenant of, String localPart) throws Exception {
        Person person = signIn(of, localPart);
        MvcResult created = mockMvc.perform(in(of, post("/kaya/machines"), person.jwt(), "{\"machineName\":\"m-" + uniqueSuffix() + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return read(created, "$.data.token");
    }

    private static Map<String, Object> file(String path, String content, String mode) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("path", path);
        file.put("mode", mode);
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
    private ResultActions propose(String machineToken, Map<String, Object>... files) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("remoteUrl", remote);
        body.put("releaseNumber", 1);
        body.put("reason", REASON);
        body.put("files", List.of(files));
        return mockMvc.perform(post("/kaya/delivery/proposals")
                .header("Authorization", "Bearer " + machineToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
    }

    private UUID current() {
        return currentVersionOf(rootPack);
    }

    /** A new current version of the root pack: a copy of v1 with the given paths replaced or, for null content, removed. */
    private UUID moveOn(Map<String, String> changes) throws Exception {
        UUID v2 = approvedVersion(rootPack, 2);
        copyFiles(v1, v2, changes.keySet().toArray(new String[0]));
        for (Map.Entry<String, String> change : changes.entrySet()) {
            if (change.getValue() != null) {
                insertFile(tenant.organization(), v2, change.getKey(), change.getValue());
            }
        }
        return v2;
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    // --- Isolation ---

    @Test
    void propose_tokenOfAnotherOrganization_reachesNothing() throws Exception {
        Tenant other = newTenant();
        String foreignToken = machineTokenOf(other, "intruder");

        propose(foreignToken, file("AGENTS.md", "# Hijacked\n", "FILE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));

        assertEquals(1, count(VERSIONS_OF_ROOT_PACK, rootPack));
        assertEquals(0, count("SELECT count(*) FROM kaya.versions WHERE organization_id = ? AND source = 'CLI'", other.organization().getId()));
    }

    @Test
    void propose_proposalIsInvisibleToAnotherOrganization() throws Exception {
        String proposalId = read(propose(token, file("AGENTS.md", "# Agents v2\n", "FILE")).andExpect(status().isCreated()).andReturn(),
                "$.data.proposalId");
        Tenant other = newTenant();
        RequestPostProcessor otherAdmin = jwtOf(other.admin().getUser());

        mockMvc.perform(in(other, get("/kaya/changes/" + proposalId), otherAdmin, null)).andExpect(status().isNotFound());
        mockMvc.perform(in(other, post("/kaya/changes/" + proposalId + "/reviews"), otherAdmin, "{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(in(other, get("/kaya/changes"), otherAdmin, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        assertEquals("IN_REVIEW", jdbcTemplate.queryForObject("SELECT status::text FROM kaya.versions WHERE id = ?::uuid",
                String.class, proposalId));
    }

    // --- Conflicts ---

    @Test
    void propose_pathDeletedMeanwhile_reportsModifiedDeleted() throws Exception {
        Map<String, String> deletion = new LinkedHashMap<>();
        deletion.put(".agents/rules/local.md", null);
        moveOn(deletion);

        propose(token, file(".agents/rules/local.md", "---\npaths: \"lib/**\"\n---\nMine\n", "FILE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].path").value(".agents/rules/local.md"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].draftChange").value("MODIFIED"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].currentChange").value("DELETED"));
    }

    @Test
    void propose_removalOfAPathModifiedMeanwhile_reportsDeletedModified() throws Exception {
        moveOn(Map.of(".agents/rules/local.md", "---\npaths: \"app/**\"\n---\nTheirs\n"));

        propose(token, removal(".agents/rules/local.md"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].draftChange").value("DELETED"))
                .andExpect(jsonPath("$.error.errorValues.conflicts[0].currentChange").value("MODIFIED"));
    }

    @Test
    void propose_stateAlreadyCurrent_isNoConflictButAnUnchangedProposal() throws Exception {
        moveOn(Map.of("AGENTS.md", "# Agents v2\n"));

        propose(token, file("AGENTS.md", "# Agents v2\n", "FILE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_016"));
    }

    // --- Review and release ---

    @Test
    void propose_executableChange_needsAnOwnerToApprove() throws Exception {
        String proposalId = read(propose(token, file(HOOK, "#!/bin/sh\nexit 0\n", "EXECUTABLE"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.executableChange").value(true)).andReturn(),
                "$.data.proposalId");

        mockMvc.perform(in(tenant, post("/kaya/changes/" + proposalId + "/reviews"), admin, "{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.errorValues.reason").value("ROLE"))
                .andExpect(jsonPath("$.error.errorValues.requiredRole").value("OWNER"));
        mockMvc.perform(in(tenant, post("/kaya/changes/" + proposalId + "/reviews"), owner, "{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void propose_approved_releasesTheProposedFilesOnly() throws Exception {
        String proposalId = read(propose(token, file("AGENTS.md", "# Agents v2\n", "FILE"), removal(".agents/rules/local.md"))
                .andExpect(status().isCreated()).andReturn(), "$.data.proposalId");

        mockMvc.perform(in(tenant, post("/kaya/changes/" + proposalId + "/reviews"), admin, "{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releases[0].number").value(2));

        MvcResult detail = mockMvc.perform(in(tenant, get("/kaya/repositories/" + repository.getId() + "/releases/2"), member, null))
                .andExpect(status().isOk()).andReturn();
        assertEquals(List.of(HOOK, ".agents/rules/style.md", "AGENTS.md"), list(detail, "$.data.files[*].path"));
        assertEquals(sha256("# Agents v2\n"), list(detail, "$.data.files[?(@.path == 'AGENTS.md')].sha256").get(0));
        assertEquals(UUID.fromString(proposalId), current());
    }

    @Test
    void propose_rejected_leavesTheReleaseUntouched() throws Exception {
        String proposalId = read(propose(token, file("AGENTS.md", "# Agents v2\n", "FILE")).andExpect(status().isCreated()).andReturn(),
                "$.data.proposalId");

        mockMvc.perform(in(tenant, post("/kaya/changes/" + proposalId + "/reviews"), admin,
                        "{\"decision\":\"REJECT\",\"comment\":\"Not this wording\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version.status").value("DRAFT"));

        assertEquals(v1, current());
        assertEquals(1, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId()));
    }
}
