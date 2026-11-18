package com.korur.oba.apps.kaya.seed.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// 4. Internal
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.service.RenderQueryService;
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

class SeedIT extends AbstractTenantIT {

    private static final String SETTINGS = """
            {"permissions":{"allow":["Bash(git status)"],"deny":["Read(./.env)"]},
             "hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":".agents/hooks/dispatch.sh"}]}],
                      "SessionStart":[{"hooks":[{"type":"command","command":".agents/hooks/dispatch.sh"}]}]}}
            """;
    private static final String SKILL = "---\nname: kaizen\ndescription: Use for Kaizen tickets\n---\n# Kaizen\n";
    private static final String SHADCN_SKILL = "---\nname: shadcn\ndescription: Use for shadcn components\n---\n# shadcn\n";
    private static final String RULE = "---\npaths: \"src/**\"\n---\nLocal rule\n";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ReleaseService releaseService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private RenderQueryService renderQueryService;
    @Autowired
    private RepositoryRepository repositoryRepository;

    private Tenant tenant;
    private RequestPostProcessor owner;
    private UUID repositoryId;

    /**
     * A repository that composes the packs org and village, in that order, before either has a
     * version: a seed fills packs, and the repositories that compose them are released.
     */
    @BeforeEach
    void setUp() {
        tenant = newTenant();
        owner = jwtOf(tenant.owner().getUser());
        Repository repository = RepositoryMocks.repository(tenant.organization().getId(),
                "github.com/seed" + uniqueSuffix().substring(0, 8) + "/village", RepositoryStatus.ACTIVE);
        repository.setId(null);
        repositoryId = repositoryRepository.saveAndFlush(repository).getId();
        composePack("org");
        composePack("village");
    }

    // --- Request building ---

    private static String sha256(String content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private MvcResult upload(String content) throws Exception {
        return mockMvc.perform(in(tenant, put("/kaya/blobs/" + sha256(content)), owner, null)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(content.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static Map<String, Object> file(String path, String content) throws Exception {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("path", path);
        file.put("sha256", sha256(content));
        file.put("mode", path.endsWith(".sh") ? "EXECUTABLE" : "FILE");
        return file;
    }

    private static Map<String, Object> link(String path, String target) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("path", path);
        file.put("mode", "SYMLINK");
        file.put("symlinkTarget", target);
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
    private UUID composePack(String name) {
        UUID packId = jdbcTemplate.queryForObject("INSERT INTO kaya.packs (organization_id, name) VALUES (?, ?) RETURNING id",
                UUID.class, tenant.organization().getId(), name);
        jdbcTemplate.update("""
                INSERT INTO kaya.repository_packs (repository_id, pack_id, position)
                SELECT ?, ?, COALESCE(MAX(position) + 1, 0) FROM kaya.repository_packs WHERE repository_id = ?
                """, repositoryId, packId, repositoryId);
        return packId;
    }

    private String seedBody(List<Map<String, Object>> sets) throws Exception {
        return seedBody(sets, null, null);
    }

    private static Map<String, Object> child(String name, String parent, List<Map<String, Object>> files) {
        Map<String, Object> set = pack(name, files);
        set.put("parent", parent);
        return set;
    }

    /** A seed body that also connects a repository using org at its root and kagi in projects/kagi. */
    private String mountingBody(String remote, List<Map<String, Object>> sets) throws Exception {
        Map<String, Object> mounted = new LinkedHashMap<>();
        mounted.put("name", "kagi");
        mounted.put("rootPath", "projects/kagi");
        Map<String, Object> repository = new LinkedHashMap<>();
        repository.put("remoteUrl", remote);
        repository.put("packs", List.of(Map.of("name", "org"), mounted));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceRef", "4f2a9c1e7b3d5a6f8e0c2b4d6f8a0c2e4b6d8f0a");
        body.put("sets", sets);
        body.put("repositories", List.of(repository));
        return objectMapper.writeValueAsString(body);
    }

    private UUID parentOf(String name) {
        return jdbcTemplate.queryForObject("SELECT parent_pack_id FROM kaya.packs WHERE organization_id = ? AND name = ?",
                UUID.class, tenant.organization().getId(), name);
    }

    private String seedBody(List<Map<String, Object>> sets, List<Map<String, Object>> notImported,
                            List<Map<String, Object>> drift) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceRef", "4f2a9c1e7b3d5a6f8e0c2b4d6f8a0c2e4b6d8f0a");
        body.put("sets", sets);
        if (notImported != null) {
            body.put("notImported", notImported);
        }
        if (drift != null) {
            body.put("drift", drift);
        }
        return objectMapper.writeValueAsString(body);
    }

    /** The organization pack of this repository's shape, with the plugin mirror link and the dispatcher. */
    private List<Map<String, Object>> orgPackFiles(String agents) throws Exception {
        List<Map<String, Object>> files = new ArrayList<>();
        files.add(file("AGENTS.md", agents));
        files.add(file(".agents/skills/kaizen/SKILL.md", SKILL));
        files.add(link(".claude/skills/kaizen", "../../.agents/skills/kaizen"));
        files.add(file(".claude/settings.json", SETTINGS));
        files.add(file(".agents/hooks/dispatch.sh", "#!/bin/sh\n"));
        return files;
    }

    private void uploadOrgPack(String agents) throws Exception {
        for (String content : List.of(agents, SKILL, SETTINGS, "#!/bin/sh\n", RULE)) {
            upload(content);
        }
    }

    private MvcResult seed(String body) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner, body))
                .andExpect(status().isOk())
                .andReturn();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    /** The packs of the organization; setUp creates two. */
    private long packCount() {
        return count("SELECT count(*) FROM kaya.packs WHERE organization_id = ?", tenant.organization().getId());
    }

    private long versionCount() {
        return count("SELECT count(*) FROM kaya.versions WHERE organization_id = ?", tenant.organization().getId());
    }

    /** How many versions of the organization render a file at the path with the mode. */
    private long versionsHolding(String path, FileMode mode) {
        UUID organizationId = tenant.organization().getId();
        long holding = 0;
        for (Map<String, Object> version : jdbcTemplate.queryForList("""
                SELECT v.id FROM kaya.versions v WHERE v.organization_id = ?
                """, organizationId)) {
            List<RenderedFile> files = renderQueryService.render(organizationId, (UUID) version.get("id"));
            for (RenderedFile file : files) {
                if (file.path().equals(path) && file.mode() == mode) {
                    holding++;
                }
            }
        }
        return holding;
    }

    // --- PUT /blobs/{sha256} ---

    @Test
    void uploadBlob_owner_storesOnceThenReportsAlreadyStored() throws Exception {
        MvcResult first = upload("# Agents\n");
        MvcResult second = upload("# Agents\n");

        assertEquals("true", read(first, "$.data.stored"));
        assertEquals("false", read(second, "$.data.stored"));
        assertEquals(sha256("# Agents\n"), read(first, "$.data.sha256"));
        assertEquals("9", read(first, "$.data.size"));
        assertEquals(1, count("SELECT count(*) FROM kaya.blobs WHERE organization_id = ?",
                tenant.organization().getId()));
    }

    @Test
    void uploadBlob_contentDoesNotMatchHash_answers400() throws Exception {
        mockMvc.perform(in(tenant, put("/kaya/blobs/" + sha256("expected")), owner, null)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("actual".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_005"))
                .andExpect(jsonPath("$.error.errorValues.actualSha256").value(sha256("actual")));
        assertEquals(0, count("SELECT count(*) FROM kaya.blobs WHERE organization_id = ?",
                tenant.organization().getId()));
    }

    @Test
    void uploadBlob_overFiveMebibytes_answers413() throws Exception {
        byte[] content = new byte[5_242_881];
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        mockMvc.perform(in(tenant, put("/kaya/blobs/" + sha256), owner, null)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(content))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("KAY_VAL_012"))
                .andExpect(jsonPath("$.error.errorValues.limitBytes").value(5_242_880));
    }

    @Test
    void uploadBlob_malformedHash_answers422() throws Exception {
        mockMvc.perform(in(tenant, put("/kaya/blobs/NOT-A-HASH"), owner, null)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("x".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
    }

    @Test
    void uploadBlobAndSeed_admin_answers403() throws Exception {
        RequestPostProcessor admin = jwtOf(tenant.admin().getUser());
        mockMvc.perform(in(tenant, put("/kaya/blobs/" + sha256("x")), admin, null)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("x".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), admin, seedBody(List.of(pack("org", List.of())))))
                .andExpect(status().isForbidden());
        assertEquals(0, count("SELECT count(*) FROM kaya.seed_runs WHERE organization_id = ?",
                tenant.organization().getId()));
    }

    // --- POST /seed-runs ---

    @Test
    void seed_twoComposedPacks_createApprovedSeedVersionsAndAReleaseEqualToTheInput() throws Exception {
        uploadOrgPack("# Agents\n");
        List<Map<String, Object>> packFiles = orgPackFiles("# Agents\n");
        List<Map<String, Object>> villageFiles = List.of(file(".agents/rules/local.md", RULE));

        MvcResult result = seed(seedBody(List.of(pack("org", packFiles), pack("village", villageFiles))));

        JsonNode run = data(result);
        assertEquals("PASSED", run.get("status").asText());
        JsonNode report = run.get("report");
        assertTrue(report.get("applied").asBoolean());
        assertEquals("CREATED", report.at("/sets/0/outcome").asText());
        assertEquals(1, report.at("/sets/0/versionNumber").asInt());
        assertTrue(report.at("/sets/0/executableChange").asBoolean(), "a pack with a hook needs an OWNER");
        assertEquals("CREATED", report.at("/sets/1/outcome").asText());
        assertEquals(1, report.at("/entities/AGENTS_FILE").asInt());
        assertEquals(1, report.at("/entities/SKILL").asInt(), "the skill link is part of its skill, no entity");
        assertEquals(1, report.at("/entities/RULE").asInt());
        assertEquals(1, report.at("/entities/HOOK").asInt());
        assertEquals(1, report.at("/entities/SETTINGS").asInt());
        assertEquals(1, report.get("releases").size());
        assertEquals(1, report.at("/releases/0/number").asInt());
        assertEquals(6, report.at("/releases/0/fileCount").asInt());
        assertEquals(0, report.get("refused").size());

        assertEquals(repositoryId.toString(), report.at("/releases/0/repositoryId").asText());
        MvcResult release = mockMvc.perform(in(tenant, get("/kaya/repositories/" + repositoryId + "/releases/1"),
                        jwtOf(tenant.member().getUser()), null))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, String> delivered = new LinkedHashMap<>();
        for (JsonNode entry : data(release).get("files")) {
            delivered.put(entry.get("path").asText(), entry.has("sha256") && !entry.get("sha256").isNull()
                    ? entry.get("sha256").asText() + "/" + entry.get("mode").asText()
                    : entry.get("target").asText() + "/" + entry.get("mode").asText());
        }
        Map<String, String> input = new LinkedHashMap<>();
        for (Map<String, Object> entry : concat(packFiles, villageFiles)) {
            input.put((String) entry.get("path"), entry.containsKey("sha256")
                    ? entry.get("sha256") + "/" + entry.get("mode")
                    : entry.get("symlinkTarget") + "/" + entry.get("mode"));
        }
        assertEquals(input, delivered);
        assertEquals(2, count("""
                SELECT count(*) FROM kaya.versions v JOIN kaya.packs s ON s.id = v.pack_id
                WHERE s.organization_id = ? AND v.status = 'APPROVED' AND v.source = 'SEED'
                  AND v.proposed_by = ? AND s.current_version_id = v.id
                """, tenant.organization().getId(), tenant.owner().getUser().getId()));
        assertEquals(1, count("SELECT count(*) FROM kaya.seed_runs WHERE organization_id = ? AND status = 'PASSED'",
                tenant.organization().getId()));
    }

    @Test
    void seed_packWithAMcpFile_refusesItAsAPathNoKindAccepts() throws Exception {
        // Arrange
        String mcp = "{ \"mcpServers\" : { \"postgres\": {\"command\": \"pg-mcp\"} } }\n";
        uploadOrgPack("# Agents\n");
        upload(mcp);
        List<Map<String, Object>> packFiles = new ArrayList<>(orgPackFiles("# Agents\n"));
        packFiles.add(file(".mcp.json", mcp));

        // Act
        JsonNode run = data(seed(seedBody(List.of(pack("org", packFiles),
                pack("village", List.of(file(".agents/rules/local.md", RULE)))))));

        // Assert
        assertEquals("PASSED", run.get("status").asText());
        JsonNode report = run.get("report");
        assertEquals(1, report.get("refused").size());
        assertEquals(".mcp.json", report.at("/refused/0/path").asText());
        assertEquals("PATH_NOT_ALLOWED", report.at("/refused/0/violations/0/rule").asText(), report.toString());
        assertEquals(1, report.at("/sets/0/refusedCount").asInt());
        assertEquals(6, report.at("/releases/0/fileCount").asInt());
        assertEquals(0, versionsHolding(".mcp.json", FileMode.FILE));
    }

    @Test
    void seed_unchangedSecondRun_createsNoVersionAndNoRelease() throws Exception {
        uploadOrgPack("# Agents\n");
        String body = seedBody(List.of(pack("org", orgPackFiles("# Agents\n")),
                pack("village", List.of(file(".agents/rules/local.md", RULE)))));
        seed(body);
        long versions = versionCount();

        JsonNode report = data(seed(body)).get("report");

        assertEquals("UNCHANGED", report.at("/sets/0/outcome").asText());
        assertEquals("UNCHANGED", report.at("/sets/1/outcome").asText());
        assertEquals(0, report.get("releases").size());
        assertEquals(versions, versionCount());
        assertEquals(2, count("SELECT count(*) FROM kaya.seed_runs WHERE organization_id = ? AND status = 'PASSED'",
                tenant.organization().getId()));
    }

    @Test
    void seed_changedPackFile_supersedesTheVersionAndReleasesAgain() throws Exception {
        uploadOrgPack("# Agents\n");
        seed(seedBody(List.of(pack("org", orgPackFiles("# Agents\n")),
                pack("village", List.of(file(".agents/rules/local.md", RULE))))));
        upload("# Agents, changed\n");

        JsonNode report = data(seed(seedBody(List.of(pack("org", orgPackFiles("# Agents, changed\n")),
                pack("village", List.of(file(".agents/rules/local.md", RULE))))))).get("report");

        assertEquals("UPDATED", report.at("/sets/0/outcome").asText());
        assertEquals(2, report.at("/sets/0/versionNumber").asInt());
        assertTrue(!report.at("/sets/0/executableChange").asBoolean(), "only AGENTS.md changed");
        assertEquals("UNCHANGED", report.at("/sets/1/outcome").asText());
        assertEquals(1, report.get("releases").size());
        assertEquals(2, report.at("/releases/0/number").asInt());
        assertEquals(1, count("""
                SELECT count(*) FROM kaya.versions v JOIN kaya.packs s ON s.id = v.pack_id
                WHERE s.organization_id = ? AND v.status = 'SUPERSEDED' AND v.number = 1
                """, tenant.organization().getId()));
    }

    @Test
    void seed_brokenSkillLink_isRefusedAndReportedWhileTheRestIsImportedAndPasses() throws Exception {
        uploadOrgPack("# Agents\n");
        upload(SHADCN_SKILL);
        List<Map<String, Object>> packFiles = orgPackFiles("# Agents\n");
        packFiles.add(file(".agents/skills/shadcn/SKILL.md", SHADCN_SKILL));
        // The link this repository has: it resolves to .agents/.agents/skills/shadcn.
        packFiles.add(link(".agents/skills/shadcn/shadcn", "../../.agents/skills/shadcn"));

        JsonNode run = data(seed(seedBody(List.of(pack("org", packFiles),
                pack("village", List.of(file(".agents/rules/local.md", RULE)))))));

        assertEquals("PASSED", run.get("status").asText());
        JsonNode report = run.get("report");
        assertTrue(report.get("applied").asBoolean());
        assertEquals(1, report.get("refused").size());
        assertEquals(".agents/skills/shadcn/shadcn", report.at("/refused/0/path").asText());
        assertEquals("org", report.at("/refused/0/setName").asText());
        assertEquals("KAY_CFG_028", report.at("/refused/0/errorCode").asText());
        assertEquals(1, report.at("/sets/0/refusedCount").asInt());
        assertEquals(6, report.at("/sets/0/fileCount").asInt());
        assertEquals(1, report.get("releases").size());
        // The shadcn skill renders its own .claude/skills/shadcn link.
        assertEquals(8, report.at("/releases/0/fileCount").asInt());
        assertEquals(0, versionsHolding(".agents/skills/shadcn/shadcn", FileMode.SYMLINK));
    }

    @Test
    void seed_danglingSkillLink_isRefusedAndReportedWhileTheRestIsImportedAndPasses() throws Exception {
        uploadOrgPack("# Agents\n");

        JsonNode run = data(seed(seedBody(List.of(pack("org", orgPackFiles("# Agents\n")),
                pack("village", List.of(file(".agents/rules/local.md", RULE),
                        link(".claude/skills/missing", "../../.agents/skills/missing")))))));

        assertEquals("PASSED", run.get("status").asText());
        JsonNode report = run.get("report");
        assertTrue(report.get("applied").asBoolean());
        assertEquals(1, report.get("refused").size());
        assertEquals(".claude/skills/missing", report.at("/refused/0/path").asText());
        assertEquals("KAY_CFG_027", report.at("/refused/0/errorCode").asText());
        assertEquals(1, report.at("/sets/1/refusedCount").asInt());
        assertEquals(1, report.get("releases").size());
        assertEquals(6, report.at("/releases/0/fileCount").asInt());
        assertEquals(0, versionsHolding(".claude/skills/missing", FileMode.SYMLINK));
    }

    @Test
    void seed_compositionWithAPathOfTwoOwners_discardsEveryWriteAndStoresTheFailedRun() throws Exception {
        uploadOrgPack("# Agents\n");
        long repositories = count("SELECT count(*) FROM kaya.repositories WHERE organization_id = ?",
                tenant.organization().getId());
        List<Map<String, Object>> packFiles = orgPackFiles("# Agents\n");
        packFiles.add(file(".agents/rules/local.md", RULE));

        JsonNode run = data(seed(seedBody(List.of(pack("org", packFiles),
                pack("village", List.of(file(".agents/rules/local.md", RULE)))))));

        assertEquals("FAILED", run.get("status").asText());
        JsonNode report = run.get("report");
        assertTrue(!report.get("applied").asBoolean());
        assertEquals("PATH_IN_TWO_SETS", report.at("/violations/0/rule").asText());
        assertTrue(report.at("/sets/0/versionId").isMissingNode(), "the version is gone");
        assertEquals(0, report.get("releases").size());
        assertEquals(2, packCount());
        assertEquals(0, versionCount());
        assertEquals(repositories, count("SELECT count(*) FROM kaya.repositories WHERE organization_id = ?",
                tenant.organization().getId()));
        assertEquals(1, count("SELECT count(*) FROM kaya.seed_runs WHERE organization_id = ? AND status = 'FAILED'",
                tenant.organization().getId()));
    }

    @Test
    void seed_skillOverTheLineLimit_isImportedWithAWarningAndPasses() throws Exception {
        String longSkill = "---\nname: long\ndescription: Use for long things\n---\n" + "line\n".repeat(1600);
        uploadOrgPack("# Agents\n");
        upload(longSkill);
        List<Map<String, Object>> packFiles = orgPackFiles("# Agents\n");
        packFiles.add(file(".agents/skills/long/SKILL.md", longSkill));

        JsonNode run = data(seed(seedBody(List.of(pack("org", packFiles),
                pack("village", List.of(file(".agents/rules/local.md", RULE)))))));

        assertEquals("PASSED", run.get("status").asText());
        JsonNode report = run.get("report");
        assertTrue(report.get("applied").asBoolean());
        assertEquals(0, report.get("refused").size());
        assertEquals(1, report.get("warnings").size());
        assertEquals("org", report.at("/warnings/0/setName").asText());
        assertEquals(".agents/skills/long/SKILL.md", report.at("/warnings/0/violation/path").asText());
        assertEquals("SKILL_TOO_LONG", report.at("/warnings/0/violation/rule").asText());
        assertEquals(6, report.at("/sets/0/fileCount").asInt());
        assertEquals(1, report.get("releases").size());
        assertEquals(1, versionsHolding(".agents/skills/long/SKILL.md", FileMode.FILE));
    }

    @Test
    void seed_agentsMdAsALinkToAFileOverFortyThousandBytes_storesTheToolLinkAndWarnsNothing() throws Exception {
        String agents = "#".repeat(40_001);
        uploadOrgPack(agents);
        List<Map<String, Object>> packFiles = orgPackFiles(agents);
        packFiles.set(0, link("AGENTS.md", ".agents/AGENTS.md"));
        packFiles.add(file(".agents/AGENTS.md", agents));

        JsonNode run = data(seed(seedBody(List.of(pack("org", packFiles),
                pack("village", List.of(file(".agents/rules/local.md", RULE)))))));

        assertEquals("PASSED", run.get("status").asText());
        JsonNode report = run.get("report");
        assertTrue(report.get("applied").asBoolean());
        assertEquals(0, report.get("refused").size());
        assertEquals(1, report.at("/entities/AGENTS_FILE").asInt(), "the link is no agents file");
        assertEquals(0, report.get("violations").size());
        assertEquals(0, report.get("warnings").size());
        assertEquals(1, report.get("releases").size());
        assertEquals(7, report.at("/releases/0/fileCount").asInt());
        assertEquals(1, versionsHolding("AGENTS.md", FileMode.SYMLINK));
        assertEquals(1, count("SELECT count(*) FROM kaya.tool_links WHERE organization_id = ? AND name = 'ROOT_AGENTS_FILE'",
                tenant.organization().getId()));
    }

    @Test
    void seed_packWithTheClaudeToolLinks_storesThemAndTheReleaseDeliversThemAsLinks() throws Exception {
        // Arrange
        uploadOrgPack("# Agents\n");
        upload("# Handoff\n");
        List<Map<String, Object>> packFiles = new ArrayList<>(orgPackFiles("# Agents\n"));
        packFiles.add(file(".agents/templates/HANDOFF.md", "# Handoff\n"));
        List<Map<String, Object>> villageFiles = List.of(file(".agents/rules/local.md", RULE),
                link(".claude/rules", "../.agents/rules"), link(".claude/templates", "../.agents/templates"));

        // Act
        JsonNode run = data(seed(seedBody(List.of(pack("org", packFiles), pack("village", villageFiles)))));

        // Assert
        assertEquals("PASSED", run.get("status").asText(), run.toString());
        JsonNode report = run.get("report");
        assertEquals(0, report.get("refused").size(), report.toString());
        assertEquals(0, report.get("violations").size(), report.toString());
        assertEquals(2, count("SELECT count(*) FROM kaya.tool_links WHERE organization_id = ?", tenant.organization().getId()));
        MvcResult release = mockMvc.perform(in(tenant, get("/kaya/repositories/" + repositoryId + "/releases/1"), jwtOf(tenant.member().getUser()), null))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, String> links = new LinkedHashMap<>();
        for (JsonNode entry : data(release).get("files")) {
            if ("SYMLINK".equals(entry.get("mode").asText())) {
                links.put(entry.get("path").asText(), entry.get("target").asText());
            }
        }
        assertEquals(Map.of(".claude/rules", "../.agents/rules", ".claude/templates", "../.agents/templates",
                ".claude/skills/kaizen", "../../.agents/skills/kaizen"), links);
    }

    @Test
    void seed_linkToASkillOfAnotherSet_isRefusedAndTheRestPasses() throws Exception {
        String badSkill = "---\nname: bad\ndescription: Use for bad things\ncolour: red\n---\n# Bad\n";
        uploadOrgPack("# Agents\n");
        upload(badSkill);
        List<Map<String, Object>> packFiles = orgPackFiles("# Agents\n");
        packFiles.add(file(".agents/skills/bad/SKILL.md", badSkill));

        JsonNode run = data(seed(seedBody(List.of(pack("org", packFiles),
                pack("village", List.of(file(".agents/rules/local.md", RULE),
                        link(".claude/skills/bad", "../../.agents/skills/bad")))))));

        assertEquals("PASSED", run.get("status").asText());
        JsonNode report = run.get("report");
        assertTrue(report.get("applied").asBoolean());
        assertEquals(0, report.get("violations").size());
        assertEquals(2, report.get("refused").size());
        assertEquals(".agents/skills/bad/SKILL.md", report.at("/refused/0/path").asText());
        assertEquals("FRONTMATTER_KEY_UNKNOWN", report.at("/refused/0/violations/0/rule").asText());
        // A link is kept only beside its own skill, so the village pack's link to the org pack's skill is refused.
        assertEquals(".claude/skills/bad", report.at("/refused/1/path").asText());
        assertEquals("KAY_CFG_027", report.at("/refused/1/errorCode").asText());
        assertEquals(0, report.get("notImported").size());
        assertEquals(1, report.at("/sets/1/fileCount").asInt());
        assertEquals(1, report.at("/sets/1/refusedCount").asInt());
        assertEquals(1, report.get("releases").size());
        assertEquals(6, report.at("/releases/0/fileCount").asInt());
        assertEquals(0, versionsHolding(".claude/skills/bad", FileMode.SYMLINK));
    }

    @Test
    void seed_notImportedAndDrift_areKeptAndDriftFailsTheRun() throws Exception {
        uploadOrgPack("# Agents\n");
        List<Map<String, Object>> notImported = List.of(Map.of("path", ".agents/memory/lessons.md", "reason", "Lessons stay in git"),
                Map.of("path", ".mcp.json", "reason", "Reported, not imported"));
        List<Map<String, Object>> drift = List.of(Map.of("path", "AGENTS.md", "pack", "org",
                "expectedSha256", sha256("# Agents\n"), "actualSha256", sha256("# Agents, edited\n")));

        JsonNode run = data(seed(seedBody(List.of(pack("org", orgPackFiles("# Agents\n")),
                pack("village", List.of(file(".agents/rules/local.md", RULE)))), notImported, drift)));

        assertEquals("FAILED", run.get("status").asText());
        JsonNode report = run.get("report");
        assertTrue(report.get("applied").asBoolean());
        assertEquals(".agents/memory/lessons.md", report.at("/notImported/0/path").asText());
        assertEquals(".mcp.json", report.at("/notImported/1/path").asText());
        assertEquals("AGENTS.md", report.at("/drift/0/path").asText());
        assertEquals(1, report.get("releases").size());
        String stored = jdbcTemplate.queryForObject("SELECT report::text FROM kaya.seed_runs WHERE id = ?::uuid",
                String.class, run.get("id").asText());
        assertEquals(".mcp.json", JsonPath.read(stored, "$.notImported[1].path"));
    }

    @Test
    void seed_blobNotUploaded_answers422WithTheMissingHashesAndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner,
                        seedBody(List.of(pack("org", List.of(file("AGENTS.md", "never uploaded")))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_009"))
                .andExpect(jsonPath("$.error.errorValues.missing[0]").value(sha256("never uploaded")));
        assertEquals(2, packCount());
        assertEquals(0, count("SELECT count(*) FROM kaya.seed_runs WHERE organization_id = ?",
                tenant.organization().getId()));
    }

    @Test
    void seed_parentNamedInTheRequest_isCreatedFirstAndBecomesTheChildsParent() throws Exception {
        upload(RULE);

        JsonNode run = data(seed(seedBody(List.of(child("kagi", "platform", List.of(file(".agents/rules/kagi.md", RULE))),
                pack("platform", List.of(file(".agents/rules/platform.md", RULE)))))));

        assertEquals("PASSED", run.get("status").asText());
        assertEquals(4, packCount());
        assertEquals(packId("platform"), parentOf("kagi"));
        assertEquals(null, parentOf("platform"));
    }

    @Test
    void seed_storedPackWithAnotherParent_answers409AndStoresNothing() throws Exception {
        upload(RULE);

        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner,
                        seedBody(List.of(child("village", "org", List.of(file(".agents/rules/local.md", RULE)))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_033"))
                .andExpect(jsonPath("$.error.errorValues.name").value("village"))
                .andExpect(jsonPath("$.error.errorValues.parent").value("org"));
        assertEquals(null, parentOf("village"));
        assertEquals(0, versionCount());
        assertEquals(0, count("SELECT count(*) FROM kaya.seed_runs WHERE organization_id = ?", tenant.organization().getId()));
    }

    @Test
    void seed_repositoryMountingASeededPack_storesTheMountAndDeliversItsAgentsFileInTheFolder() throws Exception {
        String scoped = "---\npaths: \"projects/kagi/**\"\n---\nKagi rule\n";
        uploadOrgPack("# Agents\n");
        upload("# Kagi\n");
        upload(scoped);
        String remote = "git@github.com:mount" + uniqueSuffix().substring(0, 8) + "/app.git";

        JsonNode run = data(seed(mountingBody(remote, List.of(pack("org", orgPackFiles("# Agents\n")),
                child("kagi", "org", List.of(file("AGENTS.md", "# Kagi\n"), file(".agents/rules/kagi/api.md", scoped)))))));

        assertEquals("PASSED", run.get("status").asText());
        UUID mountedRepository = jdbcTemplate.queryForObject(
                "SELECT repository_id FROM kaya.repository_packs WHERE pack_id = ? AND root_path = 'projects/kagi'",
                UUID.class, packId("kagi"));
        MvcResult release = mockMvc.perform(in(tenant, get("/kaya/repositories/" + mountedRepository + "/releases/1"),
                        jwtOf(tenant.member().getUser()), null))
                .andExpect(status().isOk())
                .andReturn();
        List<String> paths = new ArrayList<>();
        data(release).get("files").forEach(entry -> paths.add(entry.get("path").asText()));
        assertTrue(paths.contains("projects/kagi/AGENTS.md"), paths.toString());
        assertTrue(paths.contains(".agents/rules/kagi/api.md"), paths.toString());
        assertTrue(paths.contains("AGENTS.md"), paths.toString());
    }

    @Test
    void seed_unscopedRuleOfAMountedPack_answers422NamingTheRuleAndTheFolderAndStoresNothing() throws Exception {
        String unscoped = "---\npaths: \"**/*.java\"\n---\nKagi rule\n";
        uploadOrgPack("# Agents\n");
        upload(unscoped);
        String remote = "git@github.com:mount" + uniqueSuffix().substring(0, 8) + "/app.git";

        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner, mountingBody(remote, List.of(pack("org", orgPackFiles("# Agents\n")),
                        child("kagi", "org", List.of(file(".agents/rules/kagi/api.md", unscoped)))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_034"))
                .andExpect(jsonPath("$.error.errorValues.rule").value(".agents/rules/kagi/api.md"))
                .andExpect(jsonPath("$.error.errorValues.folder").value("projects/kagi"));
        assertEquals(2, packCount());
        assertEquals(0, versionCount());
        assertEquals(0, count("SELECT count(*) FROM kaya.repository_packs WHERE root_path = 'projects/kagi' AND repository_id IN "
                + "(SELECT id FROM kaya.repositories WHERE organization_id = ?)", tenant.organization().getId()));
    }

    @Test
    void seed_packNoRepositoryComposesOverridingARequiredPacksItem_failsWithTheLock() throws Exception {
        jdbcTemplate.update("UPDATE kaya.packs SET required = true WHERE organization_id = ? AND name = 'org'",
                tenant.organization().getId());
        uploadOrgPack("# Agents\n");
        List<Map<String, Object>> orgFiles = orgPackFiles("# Agents\n");
        orgFiles.add(file(".agents/rules/local.md", RULE));

        JsonNode run = data(seed(seedBody(List.of(pack("org", orgFiles),
                child("kagi", "org", List.of(file(".agents/rules/local.md", RULE)))))));

        assertEquals("FAILED", run.get("status").asText());
        JsonNode report = run.get("report");
        assertTrue(!report.get("applied").asBoolean());
        assertEquals("OVERRIDE_LOCKED", report.at("/violations/0/rule").asText());
        assertEquals(".agents/rules/local.md", report.at("/violations/0/path").asText());
        assertEquals(0, versionCount());
    }

    @Test
    void seed_malformedRequest_answers422() throws Exception {
        upload("# Agents\n");
        Map<String, Object> agents = file("AGENTS.md", "# Agents\n");
        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner, seedBody(List.of(pack("org", List.of(agents, agents))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        Map<String, Object> project = pack("village", List.of(agents));
        project.put("kind", "PROJECT");
        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner, seedBody(List.of(project))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GEN_API_001"));
        Map<String, Object> unknownParent = pack("village", List.of(agents));
        unknownParent.put("parent", "nowhere");
        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner, seedBody(List.of(unknownParent))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        assertEquals(2, packCount());
    }

    @Test
    void seed_moreFilesThanOneRunTakes_answers413AndStoresNothing() throws Exception {
        List<Map<String, Object>> sets = new ArrayList<>();
        for (int set = 0; set < 5; set++) {
            List<Map<String, Object>> files = new ArrayList<>();
            for (int index = 0; index < 4001; index++) {
                files.add(link(".agents/skills/s" + index + "/link", "../k"));
            }
            sets.add(pack("pack-" + set, files));
        }

        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner, seedBody(sets)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_011"))
                .andExpect(jsonPath("$.error.errorValues.limitFiles").value(20_000))
                .andExpect(jsonPath("$.error.errorValues.fileCount").value(20_005));
        assertEquals(2, packCount());
        assertEquals(0, count("SELECT count(*) FROM kaya.seed_runs WHERE organization_id = ?",
                tenant.organization().getId()));
    }

    /**
     * 65 stored blobs of 1 MiB each, one more than the 64 MiB a run may name. The database
     * generates the content, so the test sends none of it; the check sums the stored sizes before
     * any content is loaded, which is what keeps such a run from reaching the file rules.
     */
    @Test
    void seed_moreContentThanOneRunTakes_answers413AndStoresNothing() throws Exception {
        List<String> sha256s = jdbcTemplate.queryForList("""
                INSERT INTO kaya.blobs (organization_id, sha256, content, size)
                SELECT ?, lpad(to_hex(n), 64, '0'), convert_to(repeat('x', 1048576), 'UTF8'), 1048576
                FROM generate_series(1, 65) AS n
                RETURNING sha256
                """, String.class, tenant.organization().getId());
        List<Map<String, Object>> files = new ArrayList<>();
        for (String sha256 : sha256s) {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("path", ".agents/rules/r" + sha256.substring(62) + ".md");
            file.put("sha256", sha256);
            file.put("mode", "FILE");
            files.add(file);
        }

        mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner, seedBody(List.of(pack("big", files)))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_011"))
                .andExpect(jsonPath("$.error.errorValues.limitBytes").value(67_108_864L))
                .andExpect(jsonPath("$.error.errorValues.contentBytes").value(68_157_440L));
        assertEquals(2, packCount());
        assertEquals(0, count("SELECT count(*) FROM kaya.seed_runs WHERE organization_id = ?",
                tenant.organization().getId()));
    }

    /**
     * A pack change of a repository that holds the repository's release lock while a seed of a
     * pack the repository composes is under way. The test holds the lock itself until both
     * requests wait for it, the pack change first, so the pack change gets it first: a seed that
     * wrote its versions before taking the lock would then hold rows the pack change reads while
     * waiting for the lock the pack change holds.
     */
    @Test
    void seed_packChangeOfTheSameRepositoryHoldingTheLock_bothCompleteWithoutDeadlock() throws Exception {
        uploadOrgPack("# Agents\n");
        upload("# Agents, changed\n");
        List<Map<String, Object>> villageFiles = List.of(file(".agents/rules/local.md", RULE));
        seed(seedBody(List.of(pack("org", orgPackFiles("# Agents\n")), pack("village", villageFiles))));
        UUID org = packId("org");
        UUID village = packId("village");
        String seedBody = seedBody(List.of(pack("org", orgPackFiles("# Agents, changed\n")), pack("village", villageFiles)));
        String putBody = "{\"packs\":[{\"packId\":\"" + village + "\"},{\"packId\":\"" + org + "\"}]}";

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch held = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                releaseService.lockReleases(repositoryId);
                held.countDown();
                awaitOrFail(release);
            }));
            assertTrue(held.await(30, TimeUnit.SECONDS), "the test holds the release lock");
            Future<Integer> packChange = pool.submit(() -> mockMvc.perform(
                            in(tenant, put("/kaya/repositories/" + repositoryId + "/packs"), owner, putBody))
                    .andReturn().getResponse().getStatus());
            awaitLockWaiters(1);
            Future<Integer> seedRun = pool.submit(() -> mockMvc.perform(in(tenant, post("/kaya/seed-runs"), owner, seedBody))
                    .andReturn().getResponse().getStatus());
            awaitLockWaiters(2);
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);

            assertEquals(200, packChange.get(60, TimeUnit.SECONDS));
            assertEquals(200, seedRun.get(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(List.of(village, org), jdbcTemplate.queryForList(
                "SELECT pack_id FROM kaya.repository_packs WHERE repository_id = ? ORDER BY position", UUID.class, repositoryId),
                "the seed leaves the pack order the pack change set");
        assertEquals(List.of(1, 2, 3), jdbcTemplate.queryForList(
                "SELECT number FROM kaya.releases WHERE repository_id = ? ORDER BY number", Integer.class, repositoryId));
    }

    private static void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the lock");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Waits until the given number of sessions of this database wait for a lock: the release lock
     * of a repository, or the owner lock of a pack.
     */
    private void awaitLockWaiters(int waiters) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (count("""
                SELECT count(*) FROM pg_stat_activity
                WHERE wait_event_type = 'Lock' AND datname = current_database()
                """) < waiters) {
            assertTrue(System.nanoTime() < deadline, "waiting for " + waiters + " lock waiters");
            Thread.sleep(20);
        }
    }

    private UUID packId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM kaya.packs WHERE organization_id = ? AND name = ?",
                UUID.class, tenant.organization().getId(), name);
    }

    private static List<Map<String, Object>> concat(List<Map<String, Object>> first, List<Map<String, Object>> second) {
        List<Map<String, Object>> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
