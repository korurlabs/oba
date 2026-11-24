package com.korur.oba.apps.kaya.review.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What an approval publishes: a signed release for every repository that composes the changed pack
 * and for no other, holding exactly the files that were reviewed; and the guards around
 * it: the role an executable change needs, a base that moved on, a rule broken since the submit,
 * and the reject-and-resubmit cycle.
 */
class ApprovalReleaseIT extends AbstractVersionIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String STYLE = "---\npaths: \"src/**\"\n---\nStyle\n";
    private static final String STYLE_V2 = "---\npaths: \"src/**\"\n---\nStyle, sharper\n";
    private static final String REASON = "Sharpen the style rule";
    private static final String RELEASES = "SELECT count(*) FROM kaya.releases WHERE repository_id = ?";

    private Repository repoA;
    private Repository repoB;
    private Repository repoC;
    private UUID rootA;
    private UUID rootB;
    private UUID rootC;
    private UUID pack;
    private UUID packV1;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        pack = packWith("org", ".agents/rules/style.md", STYLE);
        packV1 = current(pack);
        repoA = saveRepository(tenant.organization());
        repoB = saveRepository(tenant.organization());
        repoC = saveRepository(tenant.organization());
        rootA = rootPackOf(repoA, "# Agents of A\n");
        rootB = rootPackOf(repoB, "# Agents of B\n");
        rootC = rootPackOf(repoC, "# Agents of C\n");
        mockMvc.perform(in(tenant, put(packsPath(repoA)), owner, packIds(pack, rootA))).andExpect(status().isOk());
        mockMvc.perform(in(tenant, put(packsPath(repoB)), owner, packIds(pack, rootB))).andExpect(status().isOk());
    }

    // --- Helpers ---

    /** The repository's own pack, composed after the packs it composes already, with its AGENTS.md. */
    private UUID rootPackOf(Repository repository, String agents) throws Exception {
        UUID set = insertRepositoryPack(tenant.organization(), repository.getId(), "ws-" + uniqueSuffix().substring(0, 8));
        insertFile(tenant.organization(), approvedVersion(set, 1), "AGENTS.md", agents);
        return set;
    }

    private UUID current(UUID setId) {
        return currentVersionOf(setId);
    }

    private static String body(Object... keysAndValues) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return JSON.writeValueAsString(map);
    }

    private UUID draftOf(UUID setId, RequestPostProcessor jwt) throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post("/kaya/drafts"), jwt, draftBody(setId)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(read(result, "$.data.id"));
    }

    private void save(UUID draft, RequestPostProcessor jwt, String path, String content) throws Exception {
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), jwt,
                        body("path", path, "content", content)))
                .andExpect(status().isOk());
    }

    private void submit(UUID draft, RequestPostProcessor jwt) throws Exception {
        mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/submit"), jwt, body("reason", REASON)))
                .andExpect(status().isOk());
    }

    private UUID inReview(UUID setId, RequestPostProcessor jwt, String path, String content) throws Exception {
        UUID draft = draftOf(setId, jwt);
        save(draft, jwt, path, content);
        submit(draft, jwt);
        return draft;
    }

    private ResultActions review(RequestPostProcessor jwt, UUID version, String decision, String comment) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/changes/" + version + "/reviews"), jwt,
                body("decision", decision, "comment", comment)));
    }

    private MvcResult releaseDetail(Repository repository, int number) throws Exception {
        return mockMvc.perform(in(tenant, get("/kaya/repositories/" + repository.getId() + "/releases/" + number), member, null))
                .andExpect(status().isOk()).andReturn();
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertVerifiable(Repository repository, int number) throws Exception {
        MvcResult detail = releaseDetail(repository, number);
        MvcResult keys = mockMvc.perform(in(tenant, get("/kaya/signing-keys"), member, null)).andExpect(status().isOk()).andReturn();
        String publicKey = list(keys, "$.data[?(@.keyId == '" + read(detail, "$.data.keyId") + "')].publicKey").get(0);
        String canonical = canonicalFromApi(tenant.id(), repository.getRemoteUrl(), detail);
        assertTrue(verifyWithRawKey(publicKey, canonical, read(detail, "$.data.signature")), canonical);
    }

    private String statusOf(UUID version) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM kaya.versions WHERE id = ?", String.class, version);
    }

    // --- Approval releases ---

    @Test
    void approve_packChange_releasesEveryComposingRepositoryAndNoOther() throws Exception {
        UUID draft = inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);

        MvcResult approved = review(admin, draft, "APPROVE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releases.length()").value(2))
                .andReturn();

        assertEquals(draft, current(pack));
        assertEquals("SUPERSEDED", statusOf(packV1));
        assertEquals(List.of(2, 2), list(approved, "$.data.releases[*].number"));
        assertEquals(2, count(RELEASES, repoA.getId()));
        assertEquals(2, count(RELEASES, repoB.getId()));
        assertEquals(0, count(RELEASES, repoC.getId()));
        assertEquals(0, count("SELECT count(*) FROM kaya.releases WHERE number = 2 AND repository_id = ?", repoC.getId()));
    }

    @Test
    void approve_packChange_releaseHoldsExactlyTheReviewedFilesAndVerifies() throws Exception {
        UUID draft = inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);
        review(admin, draft, "APPROVE", null).andExpect(status().isOk());

        for (Repository repository : List.of(repoA, repoB)) {
            MvcResult detail = releaseDetail(repository, 2);
            assertEquals(List.of(".agents/rules/style.md", "AGENTS.md"), list(detail, "$.data.files[*].path"));
            assertEquals(List.of(sha256(STYLE_V2), sha256("# Agents of " + (repository == repoA ? "A" : "B") + "\n")),
                    list(detail, "$.data.files[*].sha256"));
            assertVerifiable(repository, 2);
            // The previous release is untouched and still verifies.
            assertEquals(sha256(STYLE), list(releaseDetail(repository, 1), "$.data.files[?(@.path == '.agents/rules/style.md')].sha256").get(0));
            assertVerifiable(repository, 1);
        }
    }

    @Test
    void approve_rootPackChange_releasesOnlyThatRepository() throws Exception {
        UUID draft = inReview(rootA, owner, "AGENTS.md", "# Agents of A, v2\n");

        review(admin, draft, "APPROVE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releases.length()").value(1))
                .andExpect(jsonPath("$.data.releases[0].number").value(2));

        MvcResult detail = releaseDetail(repoA, 2);
        assertEquals(sha256("# Agents of A, v2\n"), list(detail, "$.data.files[?(@.path == 'AGENTS.md')].sha256").get(0));
        assertVerifiable(repoA, 2);
        assertEquals(1, count(RELEASES, repoB.getId()));
    }

    @Test
    void approve_rootPackAlone_releasesItsOwnFiles() throws Exception {
        UUID draft = inReview(rootC, owner, "AGENTS.md", "# Agents of C, v2\n");

        review(admin, draft, "APPROVE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releases[0].number").value(1));

        assertEquals(List.of("AGENTS.md"), list(releaseDetail(repoC, 1), "$.data.files[*].path"));
        assertVerifiable(repoC, 1);
    }

    @Test
    void approve_restoredVersion_releasesTheFilesOfTheRestoredVersion() throws Exception {
        UUID draft = inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);
        review(admin, draft, "APPROVE", null).andExpect(status().isOk());

        MvcResult restored = mockMvc.perform(in(tenant, post("/kaya/versions/" + packV1 + "/restore"), member, null))
                .andExpect(status().isCreated()).andReturn();
        UUID rollback = UUID.fromString(read(restored, "$.data.id"));
        submit(rollback, member);
        review(admin, rollback, "APPROVE", null).andExpect(status().isOk());

        MvcResult detail = releaseDetail(repoA, 3);
        assertEquals(sha256(STYLE), list(detail, "$.data.files[?(@.path == '.agents/rules/style.md')].sha256").get(0));
        assertVerifiable(repoA, 3);
        assertVerifiable(repoB, 3);
    }

    // --- Roles ---

    @Test
    void approve_hookCode_needsAnOwner() throws Exception {
        UUID draft = draftOf(pack, admin);
        save(draft, admin, ".agents/hooks/check.sh", "#!/bin/sh\nexit 0\n");
        submit(draft, admin);

        mockMvc.perform(in(tenant, get("/kaya/changes/" + draft), owner, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiredRole").value("OWNER"))
                .andExpect(jsonPath("$.data.canApprove").value(true));
        review(owner, draft, "APPROVE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version.status").value("APPROVED"));
        assertEquals(2, count(RELEASES, repoA.getId()));
    }

    @Test
    void approve_hookCodeByAnotherAdmin_returns403Role() throws Exception {
        UUID draft = draftOf(pack, owner);
        save(draft, owner, ".agents/hooks/check.sh", "#!/bin/sh\nexit 0\n");
        submit(draft, owner);
        RequestPostProcessor secondAdmin = jwtOf(createActiveMember(tenant.organization(), tenant.domain(),
                com.korur.oba.shrine.organization.enums.OrganizationMemberRole.ADMIN).getUser());

        review(secondAdmin, draft, "APPROVE", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_004"))
                .andExpect(jsonPath("$.error.errorValues.reason").value("ROLE"))
                .andExpect(jsonPath("$.error.errorValues.requiredRole").value("OWNER"));
        review(secondAdmin, draft, "COMMENT", "Needs an owner").andExpect(status().isOk());
        review(secondAdmin, draft, "REJECT", "Not now").andExpect(status().isOk());
        assertEquals(1, count(RELEASES, repoA.getId()));
    }

    @Test
    void review_memberAndAnonymous_areRefused() throws Exception {
        UUID draft = inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);

        for (String decision : List.of("APPROVE", "REJECT", "COMMENT")) {
            review(member, draft, decision, "Because").andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/kaya/changes/" + draft + "/reviews").header("X-Organization-ID", tenant.id())
                .contentType("application/json").content(body("decision", "APPROVE"))).andExpect(status().isUnauthorized());
        assertEquals(0, count("SELECT count(*) FROM kaya.reviews WHERE version_id = ?", draft));
    }

    // --- Guards ---

    @Test
    void approve_baseNoLongerCurrent_answers409AndStaysInReview() throws Exception {
        UUID draft = inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);
        UUID moved = approvedVersion(pack, 9);
        insertFile(tenant.organization(), moved, ".agents/rules/style.md", STYLE + "Elsewhere\n");

        review(admin, draft, "APPROVE", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_002"))
                .andExpect(jsonPath("$.error.errorValues.currentVersionId").value(moved.toString()));

        assertEquals("IN_REVIEW", statusOf(draft));
        assertEquals(moved, current(pack));
        assertEquals(1, count(RELEASES, repoA.getId()));
    }

    @Test
    void approve_ruleBrokenSinceTheSubmit_answers422AndKeepsEverythingUnreleased() throws Exception {
        UUID draft = inReview(rootA, owner, ".agents/rules/loud.md", "Always on and loud\n");
        // A guardrail that denies a read reaches the composed pack after the submit, and repository A
        // has no settings file to enforce it.
        insertFile(tenant.organization(), packV1, ".agents/rules/guardrails/no-secrets.md",
                "---\ncategory: SECURITY\ndescription: Keep secrets out of sessions\ndeny:\n  - Read(./.env)\n---\n"
                        + "Never read secret files.\n");

        review(admin, draft, "APPROVE", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_001"))
                .andExpect(jsonPath("$.error.errorValues.violations[?(@.rule == 'GUARDRAIL_SETTINGS_MISSING')]").exists());

        assertEquals("IN_REVIEW", statusOf(draft));
        assertEquals(1, count(RELEASES, repoA.getId()));
        assertEquals(0, count("SELECT count(*) FROM kaya.reviews WHERE version_id = ?", draft));
    }

    @Test
    void review_comment_keepsTheStatusAndRecordsWhoAndWhat() throws Exception {
        UUID draft = inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);

        review(admin, draft, "COMMENT", "Looks fine")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.releases.length()").value(0));

        assertEquals("IN_REVIEW", statusOf(draft));
        assertEquals(1, count("SELECT count(*) FROM kaya.reviews WHERE version_id = ? AND decision = 'COMMENT'", draft));
        assertEquals(1, count(RELEASES, repoA.getId()));
    }

    @Test
    void review_rejectResubmitApprove_publishesOnlyTheApproval() throws Exception {
        UUID draft = inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);
        review(admin, draft, "REJECT", "Use the sharper wording").andExpect(status().isOk());
        assertEquals("DRAFT", statusOf(draft));
        assertEquals(1, count(RELEASES, repoA.getId()));
        assertEquals(packV1, current(pack));

        save(draft, owner, ".agents/rules/style.md", "---\npaths: \"src/**\"\n---\nSharper still\n");
        submit(draft, owner);
        review(admin, draft, "APPROVE", null).andExpect(status().isOk());

        assertEquals("APPROVED", statusOf(draft));
        assertEquals(2, count("SELECT count(*) FROM kaya.reviews WHERE version_id = ?", draft));
        MvcResult detail = releaseDetail(repoA, 2);
        assertEquals(sha256("---\npaths: \"src/**\"\n---\nSharper still\n"),
                list(detail, "$.data.files[?(@.path == '.agents/rules/style.md')].sha256").get(0));
        assertVerifiable(repoA, 2);
    }

    @Test
    void review_afterApproval_answers409() throws Exception {
        UUID draft = inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);
        review(admin, draft, "APPROVE", null).andExpect(status().isOk());

        review(admin, draft, "APPROVE", null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("KAY_CFG_017"));
        review(admin, draft, "REJECT", "Too late").andExpect(status().isConflict());
        assertEquals(2, count(RELEASES, repoA.getId()));
    }

    // --- Changes list ---

    @Test
    void getChanges_pagesMostRecentlyChangedFirst() throws Exception {
        UUID second = approvedVersion(rootA, 2);
        insertFile(tenant.organization(), second, "AGENTS.md", "# Two\n");
        UUID third = approvedVersion(rootA, 3);
        insertFile(tenant.organization(), third, "AGENTS.md", "# Three\n");

        MvcResult first = mockMvc.perform(in(tenant, get("/kaya/changes?packId=" + rootA + "&page=1&amount=2"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andReturn();
        MvcResult next = mockMvc.perform(in(tenant, get("/kaya/changes?packId=" + rootA + "&page=2&amount=2"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();

        assertEquals("[3,2]", JsonPath.<Object>read(first.getResponse().getContentAsString(), "$.data[*].version.number").toString());
        assertEquals("[1]", JsonPath.<Object>read(next.getResponse().getContentAsString(), "$.data[*].version.number").toString());

        UUID oldest = currentVersionOf(rootA).equals(third)
                ? jdbcTemplate.queryForObject("SELECT id FROM kaya.versions WHERE pack_id = ? AND number = 1", UUID.class, rootA)
                : null;
        jdbcTemplate.update("UPDATE kaya.versions SET updated_at = now() + interval '1 minute' WHERE id = ?", oldest);
        MvcResult touched = mockMvc.perform(in(tenant, get("/kaya/changes?packId=" + rootA + "&page=1&amount=2"), member, null))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("[1,3]", JsonPath.<Object>read(touched.getResponse().getContentAsString(), "$.data[*].version.number").toString());
    }

    @Test
    void getChanges_filtersByStatusAndPack() throws Exception {
        UUID draft = inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);
        review(admin, draft, "APPROVE", null).andExpect(status().isOk());

        assertEquals(List.of(packV1.toString()), list(mockMvc.perform(in(tenant, get("/kaya/changes?status=SUPERSEDED"), member, null))
                .andExpect(status().isOk()).andReturn(), "$.data[*].version.id"));
        MvcResult approved = mockMvc.perform(in(tenant, get("/kaya/changes?status=APPROVED&packId=" + pack), member, null))
                .andExpect(status().isOk()).andReturn();
        assertEquals(List.of(draft.toString()), list(approved, "$.data[*].version.id"));
        mockMvc.perform(in(tenant, get("/kaya/changes?status=DRAFT&packId=" + pack), member, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(in(tenant, get("/kaya/changes?packId=" + UUID.randomUUID()), member, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getChanges_badParameters_areRefused() throws Exception {
        mockMvc.perform(in(tenant, get("/kaya/changes?status=NOPE"), member, null)).andExpect(status().is4xxClientError());
        mockMvc.perform(in(tenant, get("/kaya/changes?page=0"), member, null)).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(in(tenant, get("/kaya/changes?amount=51"), member, null)).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(in(tenant, get("/kaya/changes?amount=0"), member, null)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void getChanges_ofAnotherOrganization_showNothingOfOurs() throws Exception {
        Tenant other = newTenant();
        RequestPostProcessor otherMember = jwtOf(other.member().getUser());
        inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);

        mockMvc.perform(in(other, get("/kaya/changes"), otherMember, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(in(other, get("/kaya/changes?packId=" + pack), otherMember, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getChange_unknownOtherOrganizationOrAnonymous_isRefused() throws Exception {
        UUID draft = inReview(pack, owner, ".agents/rules/style.md", STYLE_V2);
        Tenant other = newTenant();

        mockMvc.perform(in(tenant, get("/kaya/changes/" + UUID.randomUUID()), member, null)).andExpect(status().isNotFound());
        mockMvc.perform(in(other, get("/kaya/changes/" + draft), jwtOf(other.member().getUser()), null))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/kaya/changes/" + draft).header("X-Organization-ID", tenant.id())).andExpect(status().isUnauthorized());
    }

    @Test
    void getChange_draftAndApproved_carryNoApprovalFlags() throws Exception {
        UUID draft = draftOf(pack, owner);
        save(draft, owner, ".agents/rules/style.md", STYLE_V2);

        mockMvc.perform(in(tenant, get("/kaya/changes/" + draft), admin, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.violations").doesNotExist())
                .andExpect(jsonPath("$.data.canApprove").value(false))
                .andExpect(jsonPath("$.data.canReview").value(false))
                .andExpect(jsonPath("$.data.version.packId").value(pack.toString()));
    }
}
