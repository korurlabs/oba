package com.korur.oba.apps.kaya.seed.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.mock_data.RepositoryMocks;
import com.korur.oba.apps.kaya.repository.repository.RepositoryRepository;
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The seed report and the run rules the first seed test class leaves out: every file kind the
 * report counts, the stored report, the second run of a repository that drifts, the refusals
 * that store nothing, who may seed and which organization a blob belongs to.
 */
class SeedReportIT extends AbstractTenantIT {

    private static final String AGENTS = "# Agents\n";
    private static final String RULE = "---\npaths: \"src/**\"\n---\nLocal rule\n";
    private static final String ROLE = "# Role\n";
    private static final String WORKFLOW = "# Workflow\n";
    private static final String TEMPLATE = "# Template\n";
    private static final String SUBAGENT = "---\nname: reviewer\ndescription: Reviews code\n---\nReview.\n";
    private static final String COMMAND = "Run the tests.\n";
    private static final String COUNT_RUNS = "SELECT count(*) FROM kaya.seed_runs WHERE organization_id = ?";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RepositoryRepository repositoryRepository;

    private Tenant tenant;
    private RequestPostProcessor owner;
    private UUID repositoryId;

    /** A repository that composes the packs org and village, in that order, before either has a version. */
    @BeforeEach
    void setUp() {
        tenant = newTenant();
        owner = jwtOf(tenant.owner().getUser());
        Repository repository = RepositoryMocks.repository(tenant.organization().getId(),
                "github.com/report" + uniqueSuffix().substring(0, 8) + "/village", RepositoryStatus.ACTIVE);
        repository.setId(null);
        repositoryId = repositoryRepository.saveAndFlush(repository).getId();
        composePack("org");
        composePack("village");
    }

    // --- Request building ---

    private static String sha256(String content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private void upload(Tenant into, RequestPostProcessor as, String content) throws Exception {
        mockMvc.perform(in(into, put("/kaya/blobs/" + sha256(content)), as, null)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(content.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
    }

    private void upload(String... contents) throws Exception {
        for (String content : contents) {
            upload(tenant, owner, content);
        }
    }

    private static Map<String, Object> file(String path, String content) throws Exception {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("path", path);
        file.put("sha256", sha256(content));
        file.put("mode", "FILE");
        return file;
    }

    private static Map<String, Object> pack(String name, List<Map<String, Object>> files) {
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("kind", "PACK");
        set.put("name", name);
        set.put("files", files);
        return set;
    }

    /** Creates an empty pack and appends it to the repository's pack order. */
    private void composePack(String name) {
        UUID packId = jdbcTemplate.queryForObject("INSERT INTO kaya.packs (organization_id, name) VALUES (?, ?) RETURNING id",
                UUID.class, tenant.organization().getId(), name);
        jdbcTemplate.update("""
                INSERT INTO kaya.repository_packs (repository_id, pack_id, position)
                SELECT ?, ?, COALESCE(MAX(position) + 1, 0) FROM kaya.repository_packs WHERE repository_id = ?
                """, repositoryId, packId, repositoryId);
    }

    private String body(List<Map<String, Object>> sets, List<Map<String, Object>> drift) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceRef", "4f2a9c1e7b3d5a6f8e0c2b4d6f8a0c2e4b6d8f0a");
        body.put("sets", sets);
        if (drift != null) {
            body.put("drift", drift);
        }
        return objectMapper.writeValueAsString(body);
    }

    private MvcResult seed(String requestBody) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner, requestBody))
                .andExpect(status().isOk())
                .andReturn();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private List<Map<String, Object>> allKindsPackFiles() throws Exception {
        List<Map<String, Object>> files = new ArrayList<>();
        files.add(file("AGENTS.md", AGENTS));
        files.add(file(".agents/rules/local.md", RULE));
        files.add(file(".agents/roles/reviewer.md", ROLE));
        files.add(file(".agents/workflows/review.md", WORKFLOW));
        files.add(file(".agents/templates/spec.md", TEMPLATE));
        files.add(file(".claude/agents/reviewer.md", SUBAGENT));
        return files;
    }

    // --- Report contents ---

    @Test
    void seed_everyFileKind_isCountedOncePerKindInTheReport() throws Exception {
        upload(AGENTS, RULE, ROLE, WORKFLOW, TEMPLATE, SUBAGENT);

        JsonNode run = data(seed(body(List.of(pack("org", allKindsPackFiles())), null)));

        assertEquals("PASSED", run.get("status").asText());
        JsonNode counts = run.at("/report/entities");
        for (String kind : List.of("AGENTS_FILE", "RULE", "ROLE", "WORKFLOW", "TEMPLATE", "SUBAGENT")) {
            assertEquals(1, counts.get(kind).asInt(), kind);
        }
        assertEquals(6, run.at("/report/sets/0/fileCount").asInt());
        assertEquals(0, run.at("/report/refused").size());
        assertEquals(0, run.at("/report/violations").size());
        assertEquals(1, run.at("/report/releases").size(), "the repository composes the pack");
    }

    @Test
    void seed_slashCommandFile_isRefusedAsAPathNoKindAccepts() throws Exception {
        upload(AGENTS, SUBAGENT, COMMAND);
        List<Map<String, Object>> files = List.of(file("AGENTS.md", AGENTS), file(".claude/agents/reviewer.md", SUBAGENT),
                file(".claude/commands/test.md", COMMAND));

        JsonNode run = data(seed(body(List.of(pack("org", files)), null)));

        assertEquals("PASSED", run.get("status").asText());
        assertEquals(1, run.at("/report/refused").size());
        assertEquals(".claude/commands/test.md", run.at("/report/refused/0/path").asText());
        assertEquals("PATH_NOT_ALLOWED", run.at("/report/refused/0/violations/0/rule").asText(), run.toString());
        assertEquals(1, run.at("/report/sets/0/refusedCount").asInt());
        assertTrue(run.at("/report/entities/COMMAND").isMissingNode());
    }

    @Test
    void seed_conventionDoc_isCountedAsKindConvention() throws Exception {
        String doc = "# Soft delete\n";
        upload(AGENTS, doc);

        JsonNode run = data(seed(body(List.of(pack("org", List.of(file("AGENTS.md", AGENTS),
                file("docs/private/conventions/backend/32-soft-delete.md", doc)))), null)));

        assertEquals("PASSED", run.get("status").asText());
        assertEquals(1, run.at("/report/entities/CONVENTION").asInt());
        assertEquals(1, run.at("/report/entities/AGENTS_FILE").asInt());
        assertEquals(2, run.at("/report/sets/0/fileCount").asInt());
        assertEquals(0, run.at("/report/refused").size());
    }

    @Test
    void seed_report_isStoredAsTheRunAnswers() throws Exception {
        upload(AGENTS, RULE);
        List<Map<String, Object>> drift = List.of(Map.of("path", "AGENTS.md", "pack", "org",
                "expectedSha256", sha256(AGENTS)));

        JsonNode run = data(seed(body(List.of(pack("org", List.of(file("AGENTS.md", AGENTS))),
                pack("village", List.of(file(".agents/rules/local.md", RULE)))), drift)));

        String stored = jdbcTemplate.queryForObject("SELECT report::text FROM kaya.seed_runs WHERE id = ?::uuid",
                String.class, run.get("id").asText());
        assertEquals(run.get("report"), objectMapper.readTree(stored));
        assertEquals("4f2a9c1e7b3d5a6f8e0c2b4d6f8a0c2e4b6d8f0a", jdbcTemplate.queryForObject(
                "SELECT source_ref FROM kaya.seed_runs WHERE id = ?::uuid", String.class, run.get("id").asText()));
    }

    // --- Idempotence and drift ---

    @Test
    void seed_driftOnTheSecondRun_failsTheRunAndStillCreatesNoVersion() throws Exception {
        upload(AGENTS, RULE);
        List<Map<String, Object>> sets = List.of(pack("org", List.of(file("AGENTS.md", AGENTS))),
                pack("village", List.of(file(".agents/rules/local.md", RULE))));
        seed(body(sets, null));
        long versions = count("SELECT count(*) FROM kaya.versions WHERE organization_id = ?", tenant.organization().getId());

        JsonNode run = data(seed(body(sets, List.of(Map.of("path", "AGENTS.md", "pack", "org",
                "expectedSha256", sha256(AGENTS), "actualSha256", sha256("edited"))))));

        assertEquals("FAILED", run.get("status").asText());
        assertTrue(run.at("/report/applied").asBoolean());
        assertEquals("UNCHANGED", run.at("/report/sets/0/outcome").asText());
        assertEquals(1, run.at("/report/drift").size());
        assertEquals(0, run.at("/report/releases").size());
        assertEquals(versions, count("SELECT count(*) FROM kaya.versions WHERE organization_id = ?", tenant.organization().getId()));
        assertEquals(2, count(COUNT_RUNS, tenant.organization().getId()));
    }

    // --- Refusals that store nothing ---

    @Test
    void seed_sameSetListedTwice_answers422() throws Exception {
        upload(AGENTS);
        Map<String, Object> pack = pack("org", List.of(file("AGENTS.md", AGENTS)));

        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner, body(List.of(pack, pack), null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));

        assertEquals(0, count(COUNT_RUNS, tenant.organization().getId()));
    }

    @Test
    void seed_blobOfAnotherOrganization_isMissingForThisOne() throws Exception {
        Tenant other = newTenant();
        upload(other, jwtOf(other.owner().getUser()), AGENTS);

        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner,
                        body(List.of(pack("org", List.of(file("AGENTS.md", AGENTS)))), null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_009"))
                .andExpect(jsonPath("$.error.errorValues.missing[0]").value(sha256(AGENTS)));
    }

    // --- Who may seed ---

    @Test
    void seed_memberAndAnonymous_areRefusedAndStoreNothing() throws Exception {
        String requestBody = body(List.of(pack("org", List.of())), null);
        RequestPostProcessor member = jwtOf(tenant.member().getUser());

        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), member, requestBody)).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, put("/kaya/blobs/" + sha256("x")), member, null)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("x".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/kaya/seed-runs").header("X-Organization-ID", tenant.id())
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/kaya/blobs/" + sha256("x")).header("X-Organization-ID", tenant.id())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content("x".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized());

        assertEquals(0, count(COUNT_RUNS, tenant.organization().getId()));
        assertEquals(0, count("SELECT count(*) FROM kaya.blobs WHERE organization_id = ?",
                tenant.organization().getId()));
    }

    @Test
    void releaseRead_member_canReadTheReleaseTheSeedCreated() throws Exception {
        upload(AGENTS, RULE);
        JsonNode run = data(seed(body(List.of(pack("org", List.of(file("AGENTS.md", AGENTS))),
                pack("village", List.of(file(".agents/rules/local.md", RULE)))), null)));
        assertEquals(repositoryId.toString(), run.at("/report/releases/0/repositoryId").asText());

        mockMvc.perform(in(tenant, get("/kaya/repositories/" + repositoryId + "/releases"),
                        jwtOf(tenant.member().getUser()), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].number").value(1))
                .andExpect(jsonPath("$.data[0].fileCount").value(2));
    }
}
