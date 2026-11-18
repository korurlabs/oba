package com.korur.oba.apps.kalkan.page.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.support.AbstractKalkanIT;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KalkanPageControllerIT extends AbstractKalkanIT {

    // The permission list is replaced as a whole, so the creator's MANAGE grant is kept in it.
    private void grant(String rootId, User user, String level) throws Exception {
        send(put(PAGES + "/{id}/permissions", rootId), owner,
                "[{\"userId\":\"" + admin.getId() + "\",\"level\":\"MANAGE\"},"
                        + "{\"userId\":\"" + user.getId() + "\",\"level\":\"" + level + "\"}]")
                .andExpect(status().isOk());
    }

    private long rowCount(String sql, Object... args) {
        return count(sql, args);
    }

    @Nested
    class Access {

        @Test
        void createPage_rootAsAdmin_returns201AndCreatorHoldsManage() throws Exception {
            String rootId = createdId(admin, null, "isms");

            getPage(admin, rootId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.level").value("MANAGE"))
                    .andExpect(jsonPath("$.data.path").value("isms"))
                    .andExpect(jsonPath("$.data.rootId").doesNotExist());
            assertEquals(1, rowCount("SELECT count(*) FROM kalkan.page_permissions WHERE page_id = ?::uuid "
                    + "AND user_id = ?::uuid AND level::text = 'MANAGE' AND deleted = FALSE", rootId, admin.getId()));
        }

        @Test
        void createPage_rootAsMember_returns403LevelTooLow() throws Exception {
            createPage(member, null, "isms")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("KLK_SEC_001"));
        }

        @Test
        void createPage_rootAsGuest_returns403FromGate() throws Exception {
            createPage(guest, null, "isms")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        }

        @Test
        void createPage_reservedRootSlug_returns422() throws Exception {
            for (String slug : List.of("api", "onboarding", "Invitations")) {
                createPage(admin, null, slug)
                        .andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.error.code").value("KLK_VAL_007"));
            }
        }

        @Test
        void createPage_duplicateLocale_returns422() throws Exception {
            send(post(PAGES), admin,
                    "{\"slug\":\"isms\",\"translations\":[" + translation("EN", "A", "a") + ","
                            + translation("EN", "B", "b") + "]}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_005"));
        }

        @Test
        void getPage_memberWithoutPermission_returns404() throws Exception {
            String rootId = createdId(admin, null, "isms");

            getPage(member, rootId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void getPage_hiddenAndMissing_answerTheSame() throws Exception {
            String rootId = createdId(admin, null, "isms");

            getPage(member, rootId).andExpect(status().isNotFound());
            getPage(member, UUID.randomUUID().toString())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void getPage_ownerWithoutGrant_seesEveryRootAtManage() throws Exception {
            String rootId = createdId(admin, null, "isms");

            getPage(owner, rootId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.level").value("MANAGE"));
        }

        @Test
        void getPage_childInheritsRootLevel() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "policies");
            grant(rootId, member, "EDIT");

            getPage(member, childId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.level").value("EDIT"))
                    .andExpect(jsonPath("$.data.path").value("isms/policies"));
        }

        @Test
        void getPage_guestWithViewGrant_succeedsButCannotWrite() throws Exception {
            String rootId = createdId(admin, null, "isms");
            grant(rootId, guest, "VIEW");

            getPage(guest, rootId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.level").value("VIEW"));
            updatePage(guest, rootId, "{\"compliance\":true,\"version\":0}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        }

        @Test
        void updatePage_memberWithViewOnly_returns403LevelTooLow() throws Exception {
            String rootId = createdId(admin, null, "isms");
            grant(rootId, member, "VIEW");

            updatePage(member, rootId, "{\"compliance\":true,\"version\":0}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("KLK_SEC_001"));
        }

        @Test
        void getPage_otherOrganization_returns404() throws Exception {
            String rootId = createdId(admin, null, "isms");
            Organization other = createOrganization(OrganizationStatus.ACTIVE);
            User outsider = newMember(other, OrganizationMemberRole.OWNER);

            send(get(PAGES + "/{id}", rootId), outsider, other.getId(), null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void getPage_headerNamesOrganizationOfNonMember_returns403() throws Exception {
            String rootId = createdId(admin, null, "isms");
            Organization other = createOrganization(OrganizationStatus.ACTIVE);

            send(get(PAGES + "/{id}", rootId), admin, other.getId(), null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORG_SEC_001"));
        }

        @Test
        void createPage_childUnderPageOfNoPermission_returns404() throws Exception {
            String rootId = createdId(admin, null, "isms");

            createPage(member, rootId, "policies")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }
    }

    @Nested
    class Tree {

        @Test
        void listPages_memberSeesOnlyRootsWithAGrant_parentsBeforeChildren() throws Exception {
            String visibleId = createdId(admin, null, "isms");
            createdChain(admin, visibleId, "controls", "a-5");
            createdId(admin, null, "secret");
            grant(visibleId, member, "VIEW");

            send(get(PAGES), member, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0].path").value("isms"))
                    .andExpect(jsonPath("$.data[0].level").value("VIEW"))
                    .andExpect(jsonPath("$.data[1].path").value("isms/controls"))
                    .andExpect(jsonPath("$.data[2].path").value("isms/controls/a-5"));
        }

        @Test
        void listPages_noGrant_returnsEmptyList() throws Exception {
            createdId(admin, null, "isms");

            send(get(PAGES), member, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void listPages_deletedSubtree_isHidden() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "controls");
            deletePage(admin, childId).andExpect(status().isOk());

            send(get(PAGES), admin, null)
                    .andExpect(jsonPath("$.data.length()").value(1));
        }
    }

    @Nested
    class Update {

        @Test
        void updatePage_staleVersion_returns409AndKeepsPage() throws Exception {
            String rootId = createdId(admin, null, "isms");
            updatePage(admin, rootId, "{\"compliance\":true,\"version\":0}").andExpect(status().isOk());

            updatePage(admin, rootId, "{\"compliance\":false,\"version\":0}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_002"));
            getPage(admin, rootId).andExpect(jsonPath("$.data.compliance").value(true));
        }

        @Test
        void updatePage_oneLocale_keepsTheOtherAndWritesOneRevision() throws Exception {
            String rootId = createdId(admin, null, "isms");
            updatePage(admin, rootId, "{\"version\":0,\"translations\":[" + translation("EN", "Kept", "kept") + ","
                    + translation("TR", "Baslik", "govde") + "]}").andExpect(status().isOk());
            long revisionsBefore = rowCount("SELECT count(*) FROM kalkan.page_revisions WHERE page_id = ?::uuid", rootId);

            updatePage(admin, rootId, "{\"version\":1,\"translations\":[" + translation("TR", "Yeni", "govde") + "]}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.version").value(2))
                    .andExpect(jsonPath("$.data.translations.length()").value(2));
            assertEquals(revisionsBefore + 1,
                    rowCount("SELECT count(*) FROM kalkan.page_revisions WHERE page_id = ?::uuid", rootId));
        }

        @Test
        void updatePage_nothingChanged_keepsVersionAndWritesNoRevision() throws Exception {
            String rootId = createdId(admin, null, "isms");
            long revisionsBefore = rowCount("SELECT count(*) FROM kalkan.page_revisions WHERE page_id = ?::uuid", rootId);

            updatePage(admin, rootId, "{\"version\":0,\"translations\":[" + translation("EN", "Title isms", "Body isms")
                    + "]}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.version").value(0));
            assertEquals(revisionsBefore,
                    rowCount("SELECT count(*) FROM kalkan.page_revisions WHERE page_id = ?::uuid", rootId));
        }

        @Test
        void createPage_withTwoLocales_writesOneRevisionPerLocale() throws Exception {
            String response = send(post(PAGES),
                    admin, "{\"slug\":\"isms\",\"translations\":[" + translation("EN", "A", "a") + ","
                            + translation("TR", "B", "b") + "]}")
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String rootId = JsonPath.read(response, "$.data.id");

            assertEquals(2, rowCount("SELECT count(*) FROM kalkan.page_revisions WHERE page_id = ?::uuid", rootId));
        }
    }

    @Nested
    class Move {

        @Test
        void movePage_intoOwnSubtree_returns422OwnSubtree() throws Exception {
            String rootId = createdId(admin, null, "isms");
            List<String> chain = createdChain(admin, rootId, "a", "b");

            movePage(admin, chain.get(0), chain.get(1), 0, 0)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_004"))
                    .andExpect(jsonPath("$.error.errorValues.reason").value("OWN_SUBTREE"));
        }

        @Test
        void movePage_childToTopLevel_returns422() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "a");

            movePage(admin, childId, null, 0, 0)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.errorValues.reason").value("PAGE_TO_TOP"));
        }

        @Test
        void movePage_rootUnderPage_returns422() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String otherRootId = createdId(admin, null, "handbook");

            movePage(admin, otherRootId, rootId, 0, 0)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.errorValues.reason").value("ROOT_UNDER_PAGE"));
        }

        @Test
        void movePage_beyondMaxDepth_returns422TooDeep() throws Exception {
            String rootId = createdId(admin, null, "deep");
            List<String> chain = createdChain(admin, rootId, "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10");
            String otherRootId = createdId(admin, null, "other");
            String movedId = createdId(admin, otherRootId, "m");
            createdId(admin, movedId, "m-child");

            movePage(admin, movedId, chain.get(chain.size() - 2), 0, 0)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.errorValues.reason").value("TOO_DEEP"));
        }

        @Test
        void createPage_childBeyondMaxDepth_returns422TooDeep() throws Exception {
            String rootId = createdId(admin, null, "deep");
            List<String> chain = createdChain(admin, rootId, "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10");

            createPage(admin, chain.get(chain.size() - 1), "p11")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_004"))
                    .andExpect(jsonPath("$.error.errorValues.reason").value("TOO_DEEP"));
        }

        @Test
        void movePage_acrossRoots_rewritesRootIdOfSubtreeAndFollowsNewRootAccess() throws Exception {
            String sourceId = createdId(admin, null, "source");
            String targetId = createdId(admin, null, "target");
            String movedId = createdId(admin, sourceId, "moved");
            String leafId = createdId(admin, movedId, "leaf");
            grant(targetId, member, "EDIT");

            movePage(admin, movedId, targetId, 0, 0).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rootId").value(targetId))
                    .andExpect(jsonPath("$.data.path").value("target/moved"));

            assertEquals(1, rowCount("SELECT count(*) FROM kalkan.pages WHERE id = ?::uuid AND root_id = ?::uuid",
                    leafId, targetId));
            getPage(member, leafId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.path").value("target/moved/leaf"));
        }

        @Test
        void movePage_targetRootWithoutEdit_returns404() throws Exception {
            String sourceId = createdId(admin, null, "source");
            String targetId = createdId(admin, null, "target");
            String movedId = createdId(admin, sourceId, "moved");
            grant(sourceId, member, "EDIT");

            movePage(member, movedId, targetId, 0, 0)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void movePage_staleVersion_returns409() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "a");

            movePage(admin, childId, rootId, 1, 9)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_002"));
        }
    }

    @Nested
    class DeleteAndRestore {

        @Test
        void deletePage_withSubtree_marksEveryPageAndTranslationAtOneInstant() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String branchId = createdId(admin, rootId, "branch");
            String leafId = createdId(admin, branchId, "leaf");
            String siblingId = createdId(admin, rootId, "sibling");

            deletePage(admin, branchId).andExpect(status().isOk());

            assertEquals(2, rowCount("SELECT count(*) FROM kalkan.pages WHERE deleted = TRUE AND id IN "
                    + "(?::uuid, ?::uuid)", branchId, leafId));
            assertEquals(1, rowCount("SELECT count(DISTINCT updated_at) FROM kalkan.pages WHERE id IN (?::uuid, ?::uuid)",
                    branchId, leafId));
            assertEquals(2, rowCount("SELECT count(*) FROM kalkan.pages WHERE id IN (?::uuid, ?::uuid) "
                    + "AND updated_by_id = ?::uuid", branchId, leafId, admin.getId()));
            assertEquals(2, rowCount("SELECT count(*) FROM kalkan.page_translations WHERE deleted = TRUE "
                    + "AND page_id IN (?::uuid, ?::uuid)", branchId, leafId));
            assertEquals(0, rowCount("SELECT count(*) FROM kalkan.pages WHERE deleted = TRUE AND id = ?::uuid",
                    siblingId));
            getPage(admin, leafId).andExpect(status().isNotFound());
            getPage(admin, rootId).andExpect(status().isOk());
        }

        @Test
        void deletePage_freesTheSlugAndAPushToThePathCreatesANewPage() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "policies");
            deletePage(admin, childId).andExpect(status().isOk());

            putByPath(admin, "isms/policies", "Again", false, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.outcome").value("CREATED"));
            assertEquals(2, rowCount("SELECT count(*) FROM kalkan.pages WHERE parent_id = ?::uuid", rootId));
        }

        @Test
        void deletePage_rootAsEditorOnly_returns403() throws Exception {
            String rootId = createdId(admin, null, "isms");
            grant(rootId, member, "EDIT");

            deletePage(member, rootId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("KLK_SEC_001"));
        }

        @Test
        void deletePage_childAsEditor_succeeds() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "policies");
            grant(rootId, member, "EDIT");

            deletePage(member, childId).andExpect(status().isOk());
        }

        @Test
        void restorePage_subtree_bringsBackExactlyWhatThatDeleteTook() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String branchId = createdId(admin, rootId, "branch");
            String earlierId = createdId(admin, branchId, "earlier");
            String keptId = createdId(admin, branchId, "kept");
            deletePage(admin, earlierId).andExpect(status().isOk());
            Thread.sleep(5);
            deletePage(admin, branchId).andExpect(status().isOk());

            restorePage(admin, branchId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.slug").value("branch"));

            getPage(admin, keptId).andExpect(status().isOk());
            getPage(admin, earlierId).andExpect(status().isNotFound());
            assertEquals(1, rowCount("SELECT count(*) FROM kalkan.page_translations WHERE deleted = FALSE "
                    + "AND page_id = ?::uuid", keptId));
            assertEquals(0, rowCount("SELECT count(*) FROM kalkan.page_translations WHERE deleted = FALSE "
                    + "AND page_id = ?::uuid", earlierId));
        }

        @Test
        void restorePage_livePage_returns422() throws Exception {
            String rootId = createdId(admin, null, "isms");

            restorePage(admin, rootId)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_008"));
        }

        @Test
        void restorePage_belowDeletedParent_returns409() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String branchId = createdId(admin, rootId, "branch");
            String leafId = createdId(admin, branchId, "leaf");
            deletePage(admin, leafId).andExpect(status().isOk());
            deletePage(admin, branchId).andExpect(status().isOk());

            restorePage(admin, leafId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_003"));
        }

        @Test
        void restorePage_deletedRoot_keepsGrantsAndNeedsManage() throws Exception {
            String rootId = createdId(admin, null, "isms");
            grant(rootId, member, "EDIT");
            deletePage(admin, rootId).andExpect(status().isOk());

            restorePage(member, rootId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("KLK_SEC_001"));
            restorePage(admin, rootId).andExpect(status().isOk());
            getPage(member, rootId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.level").value("EDIT"));
        }

        @Test
        void restorePage_withoutAnyLevel_returns404WhateverTheState() throws Exception {
            String rootId = createdId(admin, null, "isms");
            deletePage(admin, rootId).andExpect(status().isOk());

            restorePage(member, rootId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void restorePage_unknownPage_returns404() throws Exception {
            restorePage(admin, UUID.randomUUID().toString())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void restorePage_afterMoveThatLeavesTheDeletedChildTooDeep_isRefused() throws Exception {
            // The moved page A has a deleted child B. The move counts only A's live subtree, so A
            // fits at depth 10; restoring B would then put a live page at depth 11.
            String rootId = createdId(admin, null, "deep");
            List<String> chain = createdChain(admin, rootId, "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9");
            String otherRootId = createdId(admin, null, "other");
            String movedId = createdId(admin, otherRootId, "a");
            String deletedChildId = createdId(admin, movedId, "b");
            deletePage(admin, deletedChildId).andExpect(status().isOk());

            movePage(admin, movedId, chain.get(chain.size() - 1), 0, 0).andExpect(status().isOk());

            restorePage(admin, deletedChildId)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_004"))
                    .andExpect(jsonPath("$.error.errorValues.reason").value("TOO_DEEP"));
            List<Integer> depths = new ArrayList<>();
            send(get(PAGES), admin, null).andDo(result -> depths.add(JsonPath.<List<String>>read(
                    result.getResponse().getContentAsString(), "$.data[*].path").stream()
                    .mapToInt(path -> path.split("/").length).max().orElse(0)));
            assertEquals(10, depths.get(0), "no live page may be deeper than MAX_DEPTH");
        }
    }

    @Nested
    class UpsertByPath {

        @Test
        void putByPath_newRoot_createsAndRepeatKeepsVersionUnchanged() throws Exception {
            putByPath(admin, "docs", "Docs", false, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.outcome").value("CREATED"));
            long version = rowCount("SELECT version FROM kalkan.pages WHERE slug = 'docs' AND organization_id = ?::uuid",
                    organization.getId());

            putByPath(admin, "docs", "Docs", false, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.outcome").value("UNCHANGED"))
                    .andExpect(jsonPath("$.data.version").value(version));
            assertEquals(version, rowCount("SELECT version FROM kalkan.pages WHERE slug = 'docs' "
                    + "AND organization_id = ?::uuid", organization.getId()));
        }

        @Test
        void putByPath_secondPushWithNewText_updatesWithoutConflict() throws Exception {
            putByPath(admin, "docs", "Docs", false, false).andExpect(status().isOk());

            putByPath(admin, "docs", "Docs v2", false, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.outcome").value("UPDATED"));
            putByPath(admin, "docs", "Docs v3", false, false)
                    .andExpect(jsonPath("$.data.outcome").value("UPDATED"));
        }

        @Test
        void putByPath_afterWebEdit_returns409ChangedSincePushAndKeepsPage() throws Exception {
            String response = putByPath(admin, "docs", "Docs", false, false)
                    .andReturn().getResponse().getContentAsString();
            String pageId = JsonPath.read(response, "$.data.id");
            Number version = JsonPath.read(response, "$.data.version");
            updatePage(admin, pageId, "{\"version\":" + version + ",\"translations\":["
                    + translation("EN", "Web edit", "by hand") + "]}").andExpect(status().isOk());

            putByPath(admin, "docs", "Pushed", false, false)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_004"))
                    .andExpect(jsonPath("$.error.errorValues.path").value("docs"))
                    .andExpect(jsonPath("$.error.errorValues.updatedById").value(admin.getId().toString()));
            getPage(admin, pageId).andExpect(jsonPath("$.data.translations[0].title").value("Web edit"));
        }

        @Test
        void putByPath_afterWebEditWithForce_overwrites() throws Exception {
            String response = putByPath(admin, "docs", "Docs", false, false)
                    .andReturn().getResponse().getContentAsString();
            String pageId = JsonPath.read(response, "$.data.id");
            Number version = JsonPath.read(response, "$.data.version");
            updatePage(admin, pageId, "{\"version\":" + version + ",\"translations\":["
                    + translation("EN", "Web edit", "by hand") + "]}").andExpect(status().isOk());

            putByPath(admin, "docs", "Pushed", true, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.outcome").value("UPDATED"));
            getPage(admin, pageId).andExpect(jsonPath("$.data.translations[0].title").value("Pushed"));
            // The forced push is the new last push, so the next one does not conflict.
            putByPath(admin, "docs", "Pushed again", false, false).andExpect(status().isOk());
        }

        @Test
        void putByPath_dryRun_writesNothingAndAnswersTheOutcome() throws Exception {
            putByPath(admin, "docs", "Docs", false, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.outcome").value("CREATED"))
                    .andExpect(jsonPath("$.data.id").doesNotExist());
            assertEquals(0, rowCount("SELECT count(*) FROM kalkan.pages WHERE organization_id = ?::uuid",
                    organization.getId()));

            putByPath(admin, "docs", "Docs", false, false).andExpect(status().isOk());
            putByPath(admin, "docs", "Changed", false, true)
                    .andExpect(jsonPath("$.data.outcome").value("UPDATED"));
            assertEquals(1, rowCount("SELECT count(*) FROM kalkan.page_revisions r JOIN kalkan.pages p "
                    + "ON p.id = r.page_id WHERE p.organization_id = ?::uuid", organization.getId()));
        }

        @Test
        void putByPath_missingParentPath_returns422() throws Exception {
            putByPath(admin, "docs", "Docs", false, false).andExpect(status().isOk());

            putByPath(admin, "docs/missing/leaf", "Leaf", false, false)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_001"));
            putByPath(admin, "absent/leaf", "Leaf", false, false)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_001"));
        }

        @Test
        void putByPath_newRootAsMember_returns403() throws Exception {
            putByPath(member, "docs", "Docs", false, false)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("KLK_SEC_001"));
        }

        @Test
        void putByPath_hiddenRoot_returns404() throws Exception {
            putByPath(admin, "docs", "Docs", false, false).andExpect(status().isOk());

            putByPath(member, "docs/x", "X", false, false)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void putByPath_childUnderEditableRoot_createsChild() throws Exception {
            String response = putByPath(admin, "docs", "Docs", false, false)
                    .andReturn().getResponse().getContentAsString();
            grant(JsonPath.read(response, "$.data.id"), member, "EDIT");

            putByPath(member, "docs/guide", "Guide", false, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.outcome").value("CREATED"));
        }

        @Test
        void putByPath_invalidPath_returns422() throws Exception {
            putByPath(admin, "docs//bad", "X", false, false)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }
    }
}
