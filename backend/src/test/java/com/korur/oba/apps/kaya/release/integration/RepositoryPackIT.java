package com.korur.oba.apps.kaya.release.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.signing.service.SigningService;
import com.korur.oba.apps.kaya.version.repository.VersionRepository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The pack order of a repository over HTTP and the releases it causes: a changed order composes and
 * signs a release the release reads show, an unchanged one releases nothing, a composition that
 * breaks a rule is refused and stores nothing, and an approved pack version releases every
 * repository that composes the pack.
 */
class RepositoryPackIT extends AbstractVersionIT {

    private static final String SETTINGS_WITH_ASK = """
            {"permissions":{"allow":["Bash(git status)"],"ask":["Bash(git push:*)"],"deny":["Read(./.env)"]},
             "hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":".agents/hooks/dispatch.sh"}]}],
                      "SessionStart":[{"hooks":[{"type":"command","command":".agents/hooks/dispatch.sh"}]}]}}
            """;
    private static final String SKILL = "---\nname: kazan\ndescription: Use for Kazan tickets\npaths:\n  - \"**/*.java\"\n---\n# Kazan\n";
    private static final String LOCAL_RULE = "---\npaths: \"src/**\"\n---\nLocal rule\n";

    @Autowired
    private VersionRepository versionRepository;
    @Autowired
    private ReleaseService releaseService;
    @Autowired
    private ReleaseQueryService releaseQueryService;
    @Autowired
    private SigningService signingService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID rootPack;

    @BeforeEach
    void setUp() {
        initTenant();
    }

    /** The organization pack: always-on AGENTS.md, a skill, its plugin link, the settings and the dispatcher. */
    private UUID orgPack() {
        UUID pack = insertPack(tenant.organization(), "org");
        UUID version = approvedVersion(pack, 1);
        renderService.parse(tenant.organization().getId(), version, List.of(
                SourceFile.of("AGENTS.md", FileMode.FILE, bytes("# Agents\n")),
                SourceFile.of(".agents/skills/kazan/SKILL.md", FileMode.FILE, bytes(SKILL)),
                new SourceFile(".claude/skills/kazan", FileMode.SYMLINK, "../../.agents/skills/kazan", null, null),
                SourceFile.of(".claude/settings.json", FileMode.FILE, bytes(SETTINGS_WITH_ASK)),
                SourceFile.of(".agents/hooks/dispatch.sh", FileMode.EXECUTABLE, bytes("#!/bin/sh\n"))));
        return pack;
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    /** The repository's own pack with one path-scoped rule, which costs no always-on bytes. */
    private UUID rootPackOf(Repository repository) {
        rootPack = repositoryPackWith(repository, ".agents/rules/local.md", LOCAL_RULE);
        return rootPack;
    }

    /** The body of a pack list change: the given packs, then the repository's own pack when the test made one. */
    private String packsBody(UUID... ids) {
        if (rootPack == null) {
            return packIds(ids);
        }
        UUID[] withRoot = Arrays.copyOf(ids, ids.length + 1);
        withRoot[ids.length] = rootPack;
        return packIds(withRoot);
    }

    /** A child pack of the parent with one approved version holding the given files (path, content pairs). */
    private UUID childPackWith(String name, UUID parentPackId, String... pathsAndContents) {
        UUID pack = insertChildPack(tenant.organization(), name, parentPackId);
        insertFiles(tenant.organization(), approvedVersion(pack, 1), pathsAndContents);
        return pack;
    }

    // --- PUT /repositories/{id}/packs and the reads of a release ---

    @Test
    void replacePacks_createsASignedReleaseTheReadsShow() throws Exception {
        UUID pack = orgPack();
        Repository repository = saveRepository(tenant.organization());
        rootPackOf(repository);

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(pack)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repositoryId").value(repository.getId().toString()))
                .andExpect(jsonPath("$.data.packs[0].id").value(pack.toString()))
                .andExpect(jsonPath("$.data.release.number").value(1));

        String base = "/kaya/repositories/" + repository.getId();
        mockMvc.perform(in(tenant, get(base + "/packs"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packs.length()").value(2))
                .andExpect(jsonPath("$.data.packs[0].name").value("org"));
        mockMvc.perform(in(tenant, get(base + "/releases"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].number").value(1));
        MvcResult detail = mockMvc.perform(in(tenant, get(base + "/releases/1"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileCount").value(6))
                .andExpect(jsonPath("$.data.files[0].path").value(".agents/hooks/dispatch.sh"))
                .andExpect(jsonPath("$.data.files[0].mode").value("EXECUTABLE"))
                .andExpect(jsonPath("$.data.files[1].path").value(".agents/rules/local.md"))
                .andExpect(jsonPath("$.data.files[4].path").value(".claude/skills/kazan"))
                .andExpect(jsonPath("$.data.files[4].mode").value("SYMLINK"))
                .andExpect(jsonPath("$.data.files[4].target").value("../../.agents/skills/kazan"))
                .andExpect(jsonPath("$.data.files[5].path").value("AGENTS.md"))
                .andExpect(jsonPath("$.data.files[5].ownerId").value(pack.toString()))
                .andReturn();
        String signature = read(detail, "$.data.signature");
        String keyId = read(detail, "$.data.keyId");

        mockMvc.perform(in(tenant, get("/kaya/signing-keys"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].keyId").value(keyId));
        Release release = releaseQueryService.getEntityByNumber(tenant.organization().getId(), repository.getId(), 1);
        String canonical = releaseQueryService.toCanonicalJson(tenant.organization().getId(), release);
        assertTrue(signingService.verify(canonical, signature, keyId));
        assertTrue(canonical.contains("\"remoteUrl\":\"" + repository.getRemoteUrl() + "\""));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8))), release.getManifestSha256());

        UUID packVersion = currentVersionOf(pack);
        mockMvc.perform(in(tenant, get("/kaya/skills").param("versionId", packVersion.toString()), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("kazan"))
                .andExpect(jsonPath("$.data[0].description").value("Use for Kazan tickets"));
        mockMvc.perform(in(tenant, get("/kaya/settings").param("versionId", packVersion.toString()), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].allow[0]").value("Bash(git status)"))
                .andExpect(jsonPath("$.data[0].ask[0]").value("Bash(git push:*)"))
                .andExpect(jsonPath("$.data[0].deny[0]").value("Read(./.env)"))
                .andExpect(jsonPath("$.data[0].hooks.length()").value(2))
                .andExpect(jsonPath("$.data[0].hooks[0].event").value("PreToolUse"))
                .andExpect(jsonPath("$.data[0].hooks[0].matcher").value("Bash"))
                .andExpect(jsonPath("$.data[0].hooks[0].command").value(".agents/hooks/dispatch.sh"));
    }

    @Test
    void replacePacks_unchangedListCreatesNoRelease_changedListTheNextNumber() throws Exception {
        UUID pack = orgPack();
        Repository repository = saveRepository(tenant.organization());
        UUID root = rootPackOf(repository);

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(pack))).andExpect(status().isOk());
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(pack)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.release.number").value(1));
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packs.length()").value(1))
                .andExpect(jsonPath("$.data.release.number").value(2));
        mockMvc.perform(in(tenant, get("/kaya/repositories/" + repository.getId() + "/releases/2"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileCount").value(1));

        assertEquals(2, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId()));
        assertEquals(currentVersionOf(root), jdbcTemplate.queryForObject(
                "SELECT cause_version_id FROM kaya.releases WHERE repository_id = ? AND number = 2", UUID.class,
                repository.getId()));
    }

    @Test
    void replacePacks_withoutAnyApprovedVersion_storesThePacksWithoutARelease() throws Exception {
        UUID pack = insertPack(tenant.organization(), "empty");
        Repository repository = saveRepository(tenant.organization());

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(pack)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packs[0].id").value(pack.toString()))
                .andExpect(jsonPath("$.data.release").doesNotExist());

        mockMvc.perform(in(tenant, get("/kaya/repositories/" + repository.getId() + "/releases/1"), member, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    @Test
    void replacePacks_duplicatePack_answers422WithViolations() throws Exception {
        UUID pack = orgPack();
        Repository repository = saveRepository(tenant.organization());

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(pack, pack)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_001"))
                .andExpect(jsonPath("$.error.errorValues.violations[0].rule").value("PACK_DUPLICATE"));
        assertEquals(0, count("SELECT count(*) FROM kaya.repository_packs WHERE repository_id = ?", repository.getId()));
    }

    @Test
    void replacePacks_pathFromTwoPacks_answers409() throws Exception {
        UUID pack = orgPack();
        UUID other = packWith("other", "AGENTS.md", "# Other\n");
        Repository repository = saveRepository(tenant.organization());

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(pack, other)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_003"))
                .andExpect(jsonPath("$.error.errorValues.violations[0].rule").value("PATH_IN_TWO_SETS"))
                .andExpect(jsonPath("$.error.errorValues.violations[0].path").value("AGENTS.md"))
                .andExpect(jsonPath("$.error.errorValues.violations[0].ownerIds.length()").value(2));
        assertEquals(0, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId()));
    }

    @Test
    void replacePacks_alwaysOnFilesOverFortyThousandBytes_releases() throws Exception {
        UUID big = packWith("big", "AGENTS.md", "x".repeat(50000), ".agents/rules/loud.md", "Always on\n");
        Repository repository = saveRepository(tenant.organization());
        rootPackOf(repository);

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(big)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.release.number").value(1));
        assertEquals(1, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId()));
    }

    @Test
    void replacePacks_adminForeignRepositoryOrUnknownPack_isRefused() throws Exception {
        UUID pack = orgPack();
        Repository repository = saveRepository(tenant.organization());
        Repository foreign = saveRepository(newTenant().organization());

        mockMvc.perform(in(tenant, put(packsPath(repository)), admin, packsBody(pack)))
                .andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, put(packsPath(foreign)), owner, packsBody(pack)))
                .andExpect(status().isNotFound());
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        mockMvc.perform(in(tenant, get("/kaya/repositories/" + foreign.getId() + "/releases"), member, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void replacePacks_badBodies_areRefused() throws Exception {
        Repository repository = saveRepository(tenant.organization());
        UUID pack = packWith("p", "AGENTS.md", "# A\n");
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, "{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, "{\"packs\":[null]}"))
                .andExpect(status().isUnprocessableEntity());
        StringBuilder tooMany = new StringBuilder("{\"packs\":[");
        for (int index = 0; index < 51; index++) {
            tooMany.append(index == 0 ? "" : ",").append("{\"packId\":\"").append(UUID.randomUUID()).append("\"}");
        }
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, tooMany.append("]}").toString()))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, "{\"packs\":[{\"packId\":\"not-a-uuid\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, "{\"packs\":"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(in(tenant, put("/kaya/repositories/not-a-uuid/packs"), owner, packsBody(pack)))
                .andExpect(status().isBadRequest());
        assertEquals(0, count("SELECT count(*) FROM kaya.repository_packs WHERE repository_id = ?", repository.getId()));
    }

    @Test
    void releaseReads_badParameters_areRefused() throws Exception {
        String base = "/kaya/repositories/" + saveRepository(tenant.organization()).getId();
        mockMvc.perform(in(tenant, get(base + "/releases/abc"), member, null)).andExpect(status().isBadRequest());
        mockMvc.perform(in(tenant, get(base + "/releases?amount=0"), member, null)).andExpect(status().is4xxClientError());
        mockMvc.perform(in(tenant, get(base + "/releases?amount=51"), member, null)).andExpect(status().is4xxClientError());
    }

    // --- Composition rules the PUT applies ---

    /** The rule and path pairs a refused PUT names. */
    private List<String> violations(MvcResult result) throws Exception {
        List<String> rules = list(result, "$.error.errorValues.violations[*].rule");
        List<Object> paths = JsonPath.read(result.getResponse().getContentAsString(), "$.error.errorValues.violations[*].path");
        List<String> pairs = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            pairs.add(rules.get(index) + "@" + (index < paths.size() ? paths.get(index) : ""));
        }
        return pairs;
    }

    private MvcResult refusedPut(Repository repository, UUID... packs) throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(packs)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        assertEquals(0, count("SELECT count(*) FROM kaya.repository_packs WHERE repository_id = ?", repository.getId()));
        assertEquals(0, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId()));
        return result;
    }

    @Test
    void replacePacks_pathsThatDifferOnlyInCaseAcrossPacks_answer422() throws Exception {
        Repository repository = saveRepository(tenant.organization());
        UUID first = packWith("first", ".agents/rules/Style.md", "---\npaths: \"src/**\"\n---\nOne\n");
        UUID second = packWith("second", ".agents/rules/style.md", "---\npaths: \"src/**\"\n---\nTwo\n");

        List<String> violations = violations(refusedPut(repository, first, second));

        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("PATH_CASE_CONFLICT@.agents/rules/")),
                violations.toString());
    }

    @Test
    void replacePacks_unicodeNormalisationVariantsAcrossPacks_answer422() throws Exception {
        Repository repository = saveRepository(tenant.organization());
        UUID first = packWith("nfc", ".agents/rules/caf\u00e9.md", "---\npaths: \"src/**\"\n---\nOne\n");
        UUID second = packWith("nfd", ".agents/rules/cafe\u0301.md", "---\npaths: \"src/**\"\n---\nTwo\n");

        List<String> violations = violations(refusedPut(repository, first, second));

        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("PATH_CASE_CONFLICT@")), violations.toString());
    }

    @Test
    void replacePacks_settingsWithoutTheDispatcherRegistered_answers422() throws Exception {
        Repository repository = saveRepository(tenant.organization());
        UUID pack = packWith("bad-settings", ".agents/hooks/dispatch.sh", "#!/bin/sh\n",
                ".claude/settings.json", "{\"hooks\":{}}");

        MvcResult result = refusedPut(repository, pack);

        assertEquals("KAY_CFG_001", read(result, "$.error.code"));
        assertTrue(violations(result).contains("SETTINGS_DISPATCHER_MISSING@.claude/settings.json"), violations(result).toString());
    }

    @Test
    void replacePacks_refusalLeavesTheEarlierReleaseAndPacksAlone() throws Exception {
        Repository repository = saveRepository(tenant.organization());
        UUID good = packWith("good", "AGENTS.md", "# A\n");
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(good))).andExpect(status().isOk());
        UUID bad = packWith("bad", "AGENTS.md", "# B\n");

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(good, bad)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_003"));

        assertEquals(1, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId()));
        assertEquals(List.of(good), jdbcTemplate.queryForList(
                "SELECT pack_id FROM kaya.repository_packs WHERE repository_id = ?", UUID.class, repository.getId()));
    }

    // --- releaseVersion, the call approval and seed make ---

    @Test
    void releaseVersion_packVersion_releasesEveryComposingRepositoryWithGrowingNumbers() throws Exception {
        UUID pack = orgPack();
        Repository first = saveRepository(tenant.organization());
        Repository second = saveRepository(tenant.organization());
        mockMvc.perform(in(tenant, put(packsPath(first)), owner, packsBody(pack))).andExpect(status().isOk());
        mockMvc.perform(in(tenant, put(packsPath(second)), owner, packsBody(pack))).andExpect(status().isOk());

        UUID approved = approvedVersion(pack, 2);
        insertFile(tenant.organization(), approved, "AGENTS.md", "# Agents v2\n");

        List<Release> releases = new TransactionTemplate(transactionManager).execute(transaction ->
                releaseService.releaseVersion(tenant.organization().getId(), versionRepository.findById(approved).orElseThrow()));

        assertEquals(2, releases.size());
        releases.forEach(release -> assertEquals(2, release.getNumber()));
        releases.forEach(release -> assertEquals(1, release.getManifest().size()));
        assertEquals(1, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ? AND number = 2 "
                + "AND cause_version_id = ?", first.getId(), approved));
    }

    @Test
    void releaseVersion_versionThatBreaksAComposition_releasesNowhere() throws Exception {
        UUID pack = orgPack();
        Repository repository = saveRepository(tenant.organization());
        UUID root = rootPackOf(repository);
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packsBody(pack))).andExpect(status().isOk());
        UUID approved = approvedVersion(root, 2);
        insertFiles(tenant.organization(), approved, ".agents/rules/local.md", LOCAL_RULE, "AGENTS.md", "# Mine\n");

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(ConflictException.class,
                () -> transaction.executeWithoutResult(status -> releaseService.releaseVersion(tenant.organization().getId(),
                        versionRepository.findById(approved).orElseThrow())));
        assertEquals(1, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId()));
    }

    // --- the pack tree: chains and mounted packs ---

    @Test
    void replacePacks_mountedPack_deliversItsAgentsFileInTheFolderAndRecordsThePackVersions() throws Exception {
        UUID pack = orgPack();
        UUID kagi = childPackWith("kagi", pack, "AGENTS.md", "# Kagi\n",
                ".agents/rules/kagi/backend.md", "---\npaths: [\"projects/kagi/**\"]\n---\nKagi rule\n");
        Repository repository = saveRepository(tenant.organization());

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(pack, kagi, "projects/kagi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packs[1].id").value(kagi.toString()))
                .andExpect(jsonPath("$.data.packs[1].rootPath").value("projects/kagi"))
                .andExpect(jsonPath("$.data.release.number").value(1));

        String base = "/kaya/repositories/" + repository.getId();
        MvcResult detail = mockMvc.perform(in(tenant, get(base + "/releases/1"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packVersions.length()").value(2))
                .andExpect(jsonPath("$.data.packVersions[0].packId").value(pack.toString()))
                .andExpect(jsonPath("$.data.packVersions[0].rootPath").doesNotExist())
                .andExpect(jsonPath("$.data.packVersions[1].packId").value(kagi.toString()))
                .andExpect(jsonPath("$.data.packVersions[1].versionId").value(currentVersionOf(kagi).toString()))
                .andExpect(jsonPath("$.data.packVersions[1].rootPath").value("projects/kagi"))
                .andReturn();
        List<String> paths = list(detail, "$.data.files[*].path");
        assertTrue(paths.contains("projects/kagi/AGENTS.md"));
        assertTrue(paths.contains(".agents/rules/kagi/backend.md"));
        assertEquals(List.of(kagi.toString()), list(detail, "$.data.files[?(@.path == 'projects/kagi/AGENTS.md')].ownerId"));
        assertEquals(List.of(pack.toString()), list(detail, "$.data.files[?(@.path == 'AGENTS.md')].ownerId"));

        mockMvc.perform(in(tenant, get("/kaya/packs"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'kagi')].mountPaths[*]", hasItem("projects/kagi")))
                .andExpect(jsonPath("$.data[?(@.name == 'org')].mountPaths.length()", hasItem(0)));

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(pack, kagi, "projects/kagi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.release.number").value(1));
    }

    @Test
    void replacePacks_invalidMountFolders_answer422WithKayCfg026AndStoreNothing() throws Exception {
        UUID pack = orgPack();
        UUID kagi = childPackWith("kagi", pack, "AGENTS.md", "# Kagi\n");
        UUID kazan = childPackWith("kazan", pack, "AGENTS.md", "# Kazan\n");
        Repository repository = saveRepository(tenant.organization());

        for (String folder : List.of("../kagi", "/projects/kagi", "projects/kagi/", "docs/kagi", ".claude", "a/b/c/d/e", "")) {
            mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(pack, kagi, folder)))
                    .andExpect(status().isUnprocessableEntity());
        }
        for (String second : List.of("projects/kagi", "projects/kagi/backend", "projects")) {
            String body = "{\"packs\":[{\"packId\":\"" + pack + "\"},{\"packId\":\"" + kagi + "\",\"rootPath\":\"projects/kagi\"},"
                    + "{\"packId\":\"" + kazan + "\",\"rootPath\":\"" + second + "\"}]}";
            mockMvc.perform(in(tenant, put(packsPath(repository)), owner, body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KAY_CFG_026"));
        }
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(pack, kagi, "docs/kagi")))
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_026"));
        assertEquals(0, count("SELECT count(*) FROM kaya.repository_packs WHERE repository_id = ?", repository.getId()));
    }

    @Test
    void replacePacks_packMountedUnderItsOwnRootChain_answers422PackMountInherited() throws Exception {
        UUID pack = orgPack();
        UUID kagi = childPackWith("kagi", pack, "AGENTS.md", "# Kagi\n");
        Repository repository = saveRepository(tenant.organization());

        MvcResult refused = mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(pack, pack, "projects/kagi")))
                .andReturn();

        assertTrue(refused.getResponse().getStatus() >= 400);
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(kagi, pack, "projects/org")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.errorValues.violations[*].rule", hasItem("PACK_MOUNT_INHERITED")));
    }

    @Test
    void releaseVersion_parentVersion_releasesARepositoryThatComposesOnlyAChild() throws Exception {
        UUID parent = packWith("parent", "AGENTS.md", "# Parent\n");
        UUID child = childPackWith("child", parent, ".agents/roles/reviewer.md", "# Reviewer\n");
        Repository repository = saveRepository(tenant.organization());
        Repository unrelated = saveRepository(tenant.organization());
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(child))).andExpect(status().isOk());
        UUID other = packWith("other", "AGENTS.md", "# Other\n");
        mockMvc.perform(in(tenant, put(packsPath(unrelated)), owner, packIds(other))).andExpect(status().isOk());

        UUID approved = approvedVersion(parent, 2);
        insertFile(tenant.organization(), approved, "AGENTS.md", "# Parent v2\n");
        List<Release> releases = new TransactionTemplate(transactionManager).execute(transaction ->
                releaseService.releaseVersion(tenant.organization().getId(), versionRepository.findById(approved).orElseThrow()));

        assertEquals(1, releases.size());
        assertEquals(repository.getId(), releases.get(0).getRepositoryId());
        assertEquals(2, releases.get(0).getNumber());
        assertEquals(1, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ? AND number = 2 "
                + "AND cause_version_id = ?", repository.getId(), approved));
    }

    // --- locks and the mount rule scope ---

    private static final String WIDE_KAGI_RULE = "---\npaths: [\"**/*.java\"]\n---\nWide\n";
    private static final String SCOPED_KAGI_RULE = "---\npaths: [\"projects/kagi/**\"]\n---\nKagi rule\n";

    private static String fileBody(String path, String content) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("path", path, "content", content));
    }

    @Test
    void replacePacks_mountedPackWithAnUnscopedRule_answers422KayCfg034WithRuleAndFolder() throws Exception {
        UUID pack = orgPack();
        UUID kagi = childPackWith("kagi", pack, "AGENTS.md", "# Kagi\n", ".agents/rules/kagi/wide.md", WIDE_KAGI_RULE);
        Repository repository = saveRepository(tenant.organization());

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(pack, kagi, "projects/kagi")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_034"))
                .andExpect(jsonPath("$.error.errorValues.rule").value(".agents/rules/kagi/wide.md"))
                .andExpect(jsonPath("$.error.errorValues.folder").value("projects/kagi"))
                .andExpect(jsonPath("$.error.errorValues.pack").value(kagi.toString()));
        assertEquals(0, count("SELECT count(*) FROM kaya.repository_packs WHERE repository_id = ?", repository.getId()));
    }

    @Test
    void saveFile_ruleOfAMountedPack_answers422KayCfg034UnlessScopedToTheFolder() throws Exception {
        UUID pack = orgPack();
        UUID kagi = childPackWith("kagi", pack, "AGENTS.md", "# Kagi\n", ".agents/rules/kagi/backend.md", SCOPED_KAGI_RULE);
        Repository repository = saveRepository(tenant.organization());
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(pack, kagi, "projects/kagi")))
                .andExpect(status().isOk());
        UUID draft = startDraft(kagi, owner);

        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner, fileBody(".agents/rules/kagi/wide.md", WIDE_KAGI_RULE)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_034"))
                .andExpect(jsonPath("$.error.errorValues.rule").value(".agents/rules/kagi/wide.md"))
                .andExpect(jsonPath("$.error.errorValues.folder").value("projects/kagi"));
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                        fileBody(".agents/rules/kagi/src.md", "---\npaths: [\"./projects/kagi/src/**\"]\n---\nSrc\n")))
                .andExpect(status().isOk());
        assertNull(contentOf(draft, ".agents/rules/kagi/wide.md"));
    }

    @Test
    void saveFile_guardrailOfAMountedPackNamedGuardrails_isNotRefusedAsAnUnscopedRule() throws Exception {
        UUID pack = orgPack();
        UUID guardrails = childPackWith("guardrails", pack, "AGENTS.md", "# Guardrails\n");
        Repository repository = saveRepository(tenant.organization());
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(pack, guardrails, "projects/kagi")))
                .andExpect(status().isOk());
        UUID draft = startDraft(guardrails, owner);

        // A guardrail lies in the rule namespace of a pack named guardrails, but its frontmatter
        // forbids paths, so the mount rule scope never applies to it.
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner, fileBody(".agents/rules/guardrails/no-secrets.md",
                        "---\ncategory: SECURITY\ndescription: Keep secrets out of sessions\ndeny:\n  - Read(./.env)\n---\n"
                                + "Never read secret files.\n")))
                .andExpect(status().isOk());
    }

    @Test
    void submit_draftWithAnUnscopedRuleOfAMountedPack_answers422KayCfg034() throws Exception {
        UUID pack = orgPack();
        UUID kagi = childPackWith("kagi", pack, "AGENTS.md", "# Kagi\n");
        Repository repository = saveRepository(tenant.organization());
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(pack, kagi, "projects/kagi")))
                .andExpect(status().isOk());
        UUID draft = startDraft(kagi, owner);
        // Stored past the save check, as a draft written before the pack was mounted would hold it.
        insertFile(tenant.organization(), draft, ".agents/rules/kagi/always.md", "# Always\n");

        mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/submit"), owner, "{\"reason\":\"Add the kagi rule\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_034"))
                .andExpect(jsonPath("$.error.errorValues.rule").value(".agents/rules/kagi/always.md"))
                .andExpect(jsonPath("$.error.errorValues.folder").value("projects/kagi"));
    }

    @Test
    void saveFile_itemOfARequiredAncestor_answers422KayCfg031() throws Exception {
        UUID pack = orgPack();
        jdbcTemplate.update("UPDATE kaya.packs SET required = true WHERE id = ?", pack);
        UUID kagi = childPackWith("kagi", pack, ".agents/roles/reviewer.md", "# Reviewer\n");
        UUID draft = startDraft(kagi, owner);

        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner, fileBody("AGENTS.md", "# Kagi\n")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_031"))
                .andExpect(jsonPath("$.error.errorValues.path").value("AGENTS.md"))
                .andExpect(jsonPath("$.error.errorValues.pack").value(pack.toString()));
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner, fileBody(".agents/roles/tester.md", "# Tester\n")))
                .andExpect(status().isOk());
    }

    @Test
    void setRequired_descendantOverridesAnItem_answers422KayCfg031AndLeavesTheFlag() throws Exception {
        UUID pack = orgPack();
        UUID kagi = childPackWith("kagi", pack, "AGENTS.md", "# Kagi\n");
        jdbcTemplate.update("INSERT INTO kaya.item_overrides (organization_id, version_id, path, base_pack_id, "
                + "base_version_id) VALUES (?, ?, 'AGENTS.md', ?, ?)", tenant.organization().getId(), currentVersionOf(kagi),
                pack, currentVersionOf(pack));

        mockMvc.perform(in(tenant, put("/kaya/packs/" + pack + "/required"), owner, "{\"required\":true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_031"))
                .andExpect(jsonPath("$.error.errorValues.path").value("AGENTS.md"))
                .andExpect(jsonPath("$.error.errorValues.violations[0].ownerIds[1]").value(kagi.toString()));
        assertEquals(0, count("SELECT count(*) FROM kaya.packs WHERE id = ? AND required", pack));
    }
}
