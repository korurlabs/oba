package com.korur.oba.apps.kaya.review.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reviews of a version in review over HTTP: who may approve, approval that supersedes the current
 * version and publishes a release of the repository, reject back to draft, comments, rollback
 * drafts that the proposer never approves, and the changes list and detail.
 */
class ReviewIT extends AbstractVersionIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AGENTS = "# Agents\n";
    private static final String REASON = "Tighten the agent instructions";
    private static final String RELEASES_OF_REPOSITORY = "SELECT count(*) FROM kaya.releases WHERE repository_id = ?";

    private Repository repository;
    private UUID setId;
    private UUID v1;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        repository = saveRepository(tenant.organization());
        setId = insertRepositoryPack(tenant.organization(), repository.getId(), "ws-" + uniqueSuffix().substring(0, 8));
        v1 = approvedVersion(setId, 1);
        insertFile(tenant.organization(), v1, "AGENTS.md", AGENTS);
    }

    // --- Helpers ---

    private static String body(Object... keysAndValues) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return JSON.writeValueAsString(map);
    }

    /** A draft of the owner based on the current version, with one file saved, submitted for review. */
    private UUID inReview(UUID base, String path, String content) throws Exception {
        assertEquals(base, currentVersionOf(setId));
        UUID draft = startDraft(setId, owner);
        save(draft, path, content);
        submit(draft);
        return draft;
    }

    private void save(UUID draft, String path, String content) throws Exception {
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                        body("path", path, "content", content)))
                .andExpect(status().isOk());
    }

    private void submit(UUID draft) throws Exception {
        mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/submit"), owner, body("reason", REASON)))
                .andExpect(status().isOk());
    }

    private ResultActions review(RequestPostProcessor jwt, UUID version, String decision, String comment) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/changes/" + version + "/reviews"), jwt,
                body("decision", decision, "comment", comment)));
    }

    private ResultActions restore(RequestPostProcessor jwt, UUID version) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/versions/" + version + "/restore"), jwt, null));
    }

    private UUID currentVersion() {
        return currentVersionOf(setId);
    }

    private String statusOf(UUID version) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM kaya.versions WHERE id = ?", String.class, version);
    }

    // --- Approve ---

    @Test
    void review_approveByAdmin_supersedesTheCurrentVersionAndReleasesTheRepository() throws Exception {
        UUID draft = inReview(v1, "AGENTS.md", "# Agents v2\n");

        review(admin, draft, "APPROVE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review.decision").value("APPROVE"))
                .andExpect(jsonPath("$.data.review.reviewerId").value(tenant.admin().getUser().getId().toString()))
                .andExpect(jsonPath("$.data.version.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.releases.length()").value(1))
                .andExpect(jsonPath("$.data.releases[0].number").value(1));

        assertEquals(draft, currentVersion());
        assertEquals("SUPERSEDED", statusOf(v1));
        assertEquals(1, count(RELEASES_OF_REPOSITORY, repository.getId()));
    }

    @Test
    void review_proposerApprovesOwnChange_returns403Proposer() throws Exception {
        UUID draft = inReview(v1, "AGENTS.md", "# Agents v2\n");

        review(owner, draft, "APPROVE", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_004"))
                .andExpect(jsonPath("$.error.errorValues.reason").value("PROPOSER"));

        assertEquals("IN_REVIEW", statusOf(draft));
        assertEquals(v1, currentVersion());
    }

    @Test
    void review_executableChange_needsAnOwner() throws Exception {
        UUID draft = inReview(v1, ".agents/hooks/check.sh", "#!/bin/sh\nexit 0\n");
        OrganizationMember secondOwner = createActiveMember(tenant.organization(), tenant.domain(), OrganizationMemberRole.OWNER);

        review(admin, draft, "APPROVE", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_004"))
                .andExpect(jsonPath("$.error.errorValues.reason").value("ROLE"))
                .andExpect(jsonPath("$.error.errorValues.requiredRole").value("OWNER"));
        review(admin, draft, "COMMENT", "Why does this hook exit at once?").andExpect(status().isOk());
        review(jwtOf(secondOwner.getUser()), draft, "APPROVE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version.status").value("APPROVED"));
    }

    @Test
    void review_memberReviews_returns403() throws Exception {
        UUID draft = inReview(v1, "AGENTS.md", "# Agents v2\n");

        review(member, draft, "COMMENT", "Looks fine").andExpect(status().isForbidden());
    }

    // --- Reject and comment ---

    @Test
    void review_reject_returnsTheVersionToDraftAndKeepsTheReview() throws Exception {
        UUID draft = inReview(v1, "AGENTS.md", "# Agents v2\n");

        review(admin, draft, "REJECT", "Keep the old wording")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.releases.length()").value(0));

        assertEquals(v1, currentVersion());
        assertEquals(1, count("SELECT count(*) FROM kaya.reviews WHERE version_id = ?", draft));
        submit(draft);
        assertEquals("IN_REVIEW", statusOf(draft));
        assertNull(jdbcTemplate.queryForObject("SELECT decided_at FROM kaya.versions WHERE id = ?", Object.class, draft));
    }

    @Test
    void deleteDraft_rejectedDraft_returns409AndKeepsTheRejection() throws Exception {
        UUID draft = inReview(v1, "AGENTS.md", "# Agents v2\n");
        review(admin, draft, "COMMENT", "Which wording do we keep?").andExpect(status().isOk());
        review(admin, draft, "REJECT", "Keep the old wording").andExpect(status().isOk());

        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft), owner, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_020"))
                .andExpect(jsonPath("$.error.errorValues.versionId").value(draft.toString()));

        assertEquals("DRAFT", statusOf(draft));
        assertEquals(List.of("Which wording do we keep?", "Keep the old wording"), jdbcTemplate.queryForList(
                "SELECT comment FROM kaya.reviews WHERE version_id = ? ORDER BY created_at, id", String.class, draft));
    }

    @Test
    void review_rejectOrCommentWithoutText_returns422() throws Exception {
        UUID draft = inReview(v1, "AGENTS.md", "# Agents v2\n");

        review(admin, draft, "REJECT", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.errorValues.comment").exists());
        review(admin, draft, "COMMENT", null).andExpect(status().isUnprocessableEntity());
        assertEquals(0, count("SELECT count(*) FROM kaya.reviews WHERE version_id = ?", draft));
    }

    @Test
    void review_versionNotInReview_returns409() throws Exception {
        review(admin, v1, "APPROVE", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_017"));
    }

    @Test
    void review_versionOfAnotherOrganization_returns404() throws Exception {
        Tenant other = newTenant();
        Repository otherRepository = saveRepository(other.organization());
        UUID otherSet = insertRepositoryPack(other.organization(), otherRepository.getId(), "ws-" + uniqueSuffix().substring(0, 8));
        UUID otherVersion = approvedVersion(otherSet, 1);

        review(admin, otherVersion, "APPROVE", null).andExpect(status().isNotFound());
        restore(member, otherVersion).andExpect(status().isNotFound());
    }

    // --- Restore ---

    @Test
    void restore_unchangedRollback_anotherReviewerApprovesAndReleases() throws Exception {
        UUID v2 = inReview(v1, "AGENTS.md", "# Agents v2\n");
        review(admin, v2, "APPROVE", null).andExpect(status().isOk());

        MvcResult restored = restore(owner, v1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.source").value("ROLLBACK"))
                .andExpect(jsonPath("$.data.number").value(3))
                .andExpect(jsonPath("$.data.baseVersionId").value(v2.toString()))
                .andReturn();
        UUID rollback = UUID.fromString(read(restored, "$.data.id"));
        submit(rollback);

        review(owner, rollback, "APPROVE", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.errorValues.reason").value("PROPOSER"));
        review(admin, rollback, "APPROVE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releases[0].number").value(2));

        assertEquals(rollback, currentVersion());
        assertEquals(2, count(RELEASES_OF_REPOSITORY, repository.getId()));
        assertEquals(AGENTS, contentOf(rollback, "AGENTS.md"));
    }

    @Test
    void restore_rollbackEditedAfterRestore_proposerMayNotApprove() throws Exception {
        UUID v2 = inReview(v1, "AGENTS.md", "# Agents v2\n");
        review(admin, v2, "APPROVE", null).andExpect(status().isOk());

        UUID rollback = UUID.fromString(read(restore(owner, v1).andExpect(status().isCreated()).andReturn(), "$.data.id"));
        save(rollback, "AGENTS.md", "# Agents, edited\n");
        submit(rollback);

        review(owner, rollback, "APPROVE", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.errorValues.reason").value("PROPOSER"));
    }

    @Test
    void restore_currentOrUnreleasedVersion_returns409() throws Exception {
        restore(member, v1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_016"));

        UUID draft = inReview(v1, "AGENTS.md", "# Agents v2\n");
        restore(member, draft)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_018"));
    }

    // --- Changes list and detail ---

    @Test
    void getChanges_filtersByStatusAndCarriesTheReviews() throws Exception {
        UUID draft = inReview(v1, "AGENTS.md", "# Agents v2\n");
        review(admin, draft, "COMMENT", "Reads well").andExpect(status().isOk());

        MvcResult inReview = mockMvc.perform(in(tenant, get("/kaya/changes?status=IN_REVIEW&packId=" + setId), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].version.id").value(draft.toString()))
                .andExpect(jsonPath("$.data[0].version.packId").value(setId.toString()))
                .andExpect(jsonPath("$.data[0].requiredRole").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].reviews[0].comment").value("Reads well"))
                .andReturn();
        assertEquals(List.of(draft.toString()), list(inReview, "$.data[*].version.id"));

        mockMvc.perform(in(tenant, get("/kaya/changes?packId=" + setId), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].version.id").value(draft.toString()));
    }

    @Test
    void getChange_inReview_tellsWhoMayApprove() throws Exception {
        UUID draft = inReview(v1, "AGENTS.md", "# Agents v2\n");

        mockMvc.perform(in(tenant, get("/kaya/changes/" + draft), admin, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violations.length()").value(0))
                .andExpect(jsonPath("$.data.canApprove").value(true))
                .andExpect(jsonPath("$.data.canReview").value(true));
        mockMvc.perform(in(tenant, get("/kaya/changes/" + draft), owner, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canApprove").value(false))
                .andExpect(jsonPath("$.data.canReview").value(false));
        mockMvc.perform(in(tenant, get("/kaya/changes/" + v1), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violations").doesNotExist())
                .andExpect(jsonPath("$.data.canApprove").value(false));
    }

    // --- Draft violations ---

    @Test
    void getChange_ownDraft_showsViolationsToTheProposerOnly() throws Exception {
        UUID draft = startDraft(setId, owner);
        save(draft, "AGENTS.md", "# Agents v2\n");

        mockMvc.perform(in(tenant, get("/kaya/changes/" + draft), owner, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violations.length()").value(0))
                .andExpect(jsonPath("$.data.canApprove").value(false))
                .andExpect(jsonPath("$.data.canReview").value(false));
        mockMvc.perform(in(tenant, get("/kaya/changes/" + draft), admin, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violations").doesNotExist());
    }

    // --- File history ---

    @Test
    void history_listsTheVersionsThatChangedThePath_withReviews() throws Exception {
        UUID approved = inReview(v1, "AGENTS.md", "# Agents v2\n");
        review(admin, approved, "APPROVE", "Reads well").andExpect(status().isOk());
        UUID ownerDraft = startDraft(setId, owner);
        save(ownerDraft, "AGENTS.md", "# Agents v3\n");
        UUID memberDraft = startDraft(setId, member);
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + memberDraft + "/files"), member,
                        body("path", ".agents/templates/t.md", "content", "T\n")))
                .andExpect(status().isOk());

        MvcResult asOwner = mockMvc.perform(in(tenant, get("/kaya/changes/history")
                        .param("versionId", v1.toString()).param("path", "AGENTS.md"), owner, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].change").value("MODIFIED"))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data[1].current").value(true))
                .andExpect(jsonPath("$.data[1].reviews[0].comment").value("Reads well"))
                .andExpect(jsonPath("$.data[2].change").value("ADDED"))
                .andExpect(jsonPath("$.data[2].current").value(false))
                .andReturn();
        MvcResult asMember = mockMvc.perform(in(tenant, get("/kaya/changes/history")
                        .param("versionId", approved.toString()).param("path", "AGENTS.md"), member, null))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(List.of(ownerDraft.toString(), approved.toString(), v1.toString()),
                list(asOwner, "$.data[*].versionId"));
        assertEquals(List.of(approved.toString(), v1.toString()), list(asMember, "$.data[*].versionId"));
    }

    @Test
    void history_versionThatLeftThePathAlone_isOmittedAndADeletionHasNoHash() throws Exception {
        // Arrange
        UUID added = inReview(v1, ".agents/templates/t.md", "T\n");
        review(admin, added, "APPROVE", null).andExpect(status().isOk());
        UUID deleting = startDraft(setId, owner);
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + deleting + "/files").param("path", ".agents/templates/t.md"), owner, null))
                .andExpect(status().isOk());
        submit(deleting);
        review(admin, deleting, "APPROVE", null).andExpect(status().isOk());

        // Act
        MvcResult agents = mockMvc.perform(in(tenant, get("/kaya/changes/history")
                        .param("versionId", deleting.toString()).param("path", "AGENTS.md"), member, null))
                .andExpect(status().isOk()).andReturn();
        MvcResult template = mockMvc.perform(in(tenant, get("/kaya/changes/history")
                        .param("versionId", deleting.toString()).param("path", ".agents/templates/t.md"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].change").value("DELETED"))
                .andExpect(jsonPath("$.data[0].sha256").doesNotExist())
                .andExpect(jsonPath("$.data[0].current").value(true))
                .andExpect(jsonPath("$.data[1].change").value("ADDED"))
                .andReturn();

        // Assert
        assertEquals(List.of(v1.toString()), list(agents, "$.data[*].versionId"));
        assertEquals(List.of(deleting.toString(), added.toString()), list(template, "$.data[*].versionId"));
    }

    @Test
    void history_missingPath_answers422_andAnotherOrganization_answers404() throws Exception {
        mockMvc.perform(in(tenant, get("/kaya/changes/history").param("versionId", v1.toString()), member, null))
                .andExpect(status().isUnprocessableEntity());

        Tenant other = newTenant();
        mockMvc.perform(in(other, get("/kaya/changes/history").param("versionId", v1.toString()).param("path", "AGENTS.md"),
                        jwtOf(other.owner().getUser()), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }
}
