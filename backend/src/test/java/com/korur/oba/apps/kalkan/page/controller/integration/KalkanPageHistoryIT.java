package com.korur.oba.apps.kalkan.page.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.support.AbstractKalkanIT;
import com.korur.oba.shrine.identity.entity.User;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The history, trash and recent-changes reads of the page tree.
 */
class KalkanPageHistoryIT extends AbstractKalkanIT {

    // The permission list is replaced as a whole, so the creator's MANAGE grant is kept in it.
    private void grant(String rootId, User user, String level) throws Exception {
        send(put(PAGES + "/{id}/permissions", rootId), owner,
                "[{\"userId\":\"" + admin.getId() + "\",\"level\":\"MANAGE\"},"
                        + "{\"userId\":\"" + user.getId() + "\",\"level\":\"" + level + "\"}]")
                .andExpect(status().isOk());
    }

    private String createdBilingualRoot(String slug) throws Exception {
        String response = send(post(PAGES), admin, "{\"slug\":\"" + slug + "\",\"translations\":["
                + translation("TR", "Baslik " + slug, "govde") + "," + translation("EN", "Title " + slug, "body")
                + "]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private String firstRevisionId(User caller, String pageId) throws Exception {
        String response = send(get(PAGES + "/{id}/revisions", pageId), caller, null)
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data[0].id");
    }

    @Nested
    class Revisions {

        @Test
        void getRevisions_afterAnEdit_listsNewestVersionFirstWithoutBodies() throws Exception {
            String rootId = createdBilingualRoot("isms");
            updatePage(admin, rootId, "{\"version\":0,\"translations\":[" + translation("EN", "Renamed", "body")
                    + "]}").andExpect(status().isOk());

            send(get(PAGES + "/{id}/revisions", rootId), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0].version").value(1))
                    .andExpect(jsonPath("$.data[0].locale").value("EN"))
                    .andExpect(jsonPath("$.data[0].title").value("Renamed"))
                    .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.data[0].createdByName").isNotEmpty())
                    .andExpect(jsonPath("$.data[0].body").doesNotExist())
                    .andExpect(jsonPath("$.data[1].version").value(0))
                    .andExpect(jsonPath("$.data[2].version").value(0));
        }

        @Test
        void getRevisions_memberWithoutGrant_returns404() throws Exception {
            String rootId = createdId(admin, null, "isms");

            send(get(PAGES + "/{id}/revisions", rootId), member, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void getRevisions_guestWithView_returns200() throws Exception {
            String rootId = createdId(admin, null, "isms");
            grant(rootId, guest, "VIEW");

            send(get(PAGES + "/{id}/revisions", rootId), guest, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        void getRevisions_deletedPage_returns404() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "controls");
            deletePage(admin, childId).andExpect(status().isOk());

            send(get(PAGES + "/{id}/revisions", childId), admin, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void getRevision_ofThePage_returnsBody() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String revisionId = firstRevisionId(admin, rootId);

            send(get(PAGES + "/{id}/revisions/{revisionId}", rootId, revisionId), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(revisionId))
                    .andExpect(jsonPath("$.data.locale").value("EN"))
                    .andExpect(jsonPath("$.data.title").value("Title isms"))
                    .andExpect(jsonPath("$.data.body").value("Body isms"))
                    .andExpect(jsonPath("$.data.version").value(0))
                    .andExpect(jsonPath("$.data.createdByName").isNotEmpty());
        }

        @Test
        void getRevision_ofAnotherPage_returns404RevisionNotFound() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String otherId = createdId(admin, rootId, "controls");
            String otherRevisionId = firstRevisionId(admin, otherId);

            send(get(PAGES + "/{id}/revisions/{revisionId}", rootId, otherRevisionId), admin, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_002"));
            send(get(PAGES + "/{id}/revisions/{revisionId}", rootId, UUID.randomUUID()), admin, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_002"));
        }

        @Test
        void getRevision_hiddenPage_returnsPageNotFound() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String revisionId = firstRevisionId(admin, rootId);

            send(get(PAGES + "/{id}/revisions/{revisionId}", rootId, revisionId), member, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }
    }

    @Nested
    class Trash {

        @Test
        void getTrash_deletedSubtree_listsOnlyItsTopPage() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "controls");
            createdId(admin, childId, "a-5");
            deletePage(admin, childId).andExpect(status().isOk());

            send(get(PAGES + "/trash"), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(childId))
                    .andExpect(jsonPath("$.data[0].title").value("Title controls"))
                    .andExpect(jsonPath("$.data[0].path").value("isms/controls"))
                    .andExpect(jsonPath("$.data[0].descendantCount").value(1))
                    .andExpect(jsonPath("$.data[0].deletedAt").isNotEmpty())
                    .andExpect(jsonPath("$.data[0].deletedByName").isNotEmpty());
        }

        @Test
        void getTrash_earlierDeleteBelow_listsEachDeleteNewestFirst() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "controls");
            String grandchildId = createdId(admin, childId, "a-5");
            deletePage(admin, grandchildId).andExpect(status().isOk());
            deletePage(admin, childId).andExpect(status().isOk());

            send(get(PAGES + "/trash"), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(childId))
                    .andExpect(jsonPath("$.data[0].descendantCount").value(0))
                    .andExpect(jsonPath("$.data[1].id").value(grandchildId))
                    .andExpect(jsonPath("$.data[1].path").value("isms/controls/a-5"));
        }

        @Test
        void getTrash_locale_picksThatTitleElseTheFirst() throws Exception {
            String rootId = createdBilingualRoot("isms");
            String childId = createdId(admin, rootId, "controls");
            deletePage(admin, rootId).andExpect(status().isOk());

            send(get(PAGES + "/trash").param("locale", "EN"), admin, null)
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(rootId))
                    .andExpect(jsonPath("$.data[0].title").value("Title isms"))
                    .andExpect(jsonPath("$.data[0].descendantCount").value(1));
            send(get(PAGES + "/trash"), admin, null)
                    .andExpect(jsonPath("$.data[0].title").value("Baslik isms"));
            restorePage(admin, rootId).andExpect(status().isOk());
            deletePage(admin, childId).andExpect(status().isOk());
            send(get(PAGES + "/trash").param("locale", "TR"), admin, null)
                    .andExpect(jsonPath("$.data[0].title").value("Title controls"));
        }

        @Test
        void getTrash_restoredPage_leavesTheTrash() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "controls");
            deletePage(admin, childId).andExpect(status().isOk());
            restorePage(admin, childId).andExpect(status().isOk());

            send(get(PAGES + "/trash"), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        // A deleted root and a deleted page below another root, both granted to the member at the
        // given level before the deletes, since a deleted root's permissions can no longer be edited.
        private String[] deletedRootAndChild(String level) throws Exception {
            String liveRootId = createdId(admin, null, "isms");
            String childId = createdId(admin, liveRootId, "controls");
            String deletedRootId = createdId(admin, null, "policies");
            grant(liveRootId, member, level);
            grant(deletedRootId, member, level);
            deletePage(admin, childId).andExpect(status().isOk());
            deletePage(admin, deletedRootId).andExpect(status().isOk());
            return new String[] {childId, deletedRootId};
        }

        @Test
        void getTrash_memberWithEdit_seesDeletedPageBelowButNotDeletedRoot() throws Exception {
            String[] deleted = deletedRootAndChild("EDIT");

            send(get(PAGES + "/trash"), member, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(deleted[0]))
                    .andExpect(jsonPath("$.data[0].path").value("isms/controls"));
        }

        @Test
        void getTrash_memberWithManage_seesDeletedRootAndPageBelow() throws Exception {
            String[] deleted = deletedRootAndChild("MANAGE");

            send(get(PAGES + "/trash"), member, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(deleted[1]))
                    .andExpect(jsonPath("$.data[1].id").value(deleted[0]));
        }

        @Test
        void getTrash_memberWithView_seesNothing() throws Exception {
            deletedRootAndChild("VIEW");

            send(get(PAGES + "/trash"), member, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getTrash_ownerWithoutGrant_seesDeletedRoot() throws Exception {
            String rootId = createdId(admin, null, "isms");
            deletePage(admin, rootId).andExpect(status().isOk());

            send(get(PAGES + "/trash"), owner, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].path").value("isms"));
        }

        @Test
        void getTrash_unknownLocale_returns422() throws Exception {
            send(get(PAGES + "/trash").param("locale", "DE"), admin, null)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"))
                    .andExpect(jsonPath("$.error.errorValues.locale").exists());
        }

        @Test
        void getTrash_guest_returns403FromGate() throws Exception {
            send(get(PAGES + "/trash"), guest, null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        }
    }

    @Nested
    class Recent {

        @Test
        void getRecent_lastChangedFirst_withPathAndRootSlug() throws Exception {
            String rootId = createdId(admin, null, "policies");
            String firstId = createdId(admin, rootId, "access");
            createdId(admin, rootId, "backup");
            updatePage(admin, firstId, "{\"compliance\":true,\"version\":0}").andExpect(status().isOk());

            send(get(PAGES + "/recent"), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0].id").value(firstId))
                    .andExpect(jsonPath("$.data[0].title").value("Title access"))
                    .andExpect(jsonPath("$.data[0].path").value("policies/access"))
                    .andExpect(jsonPath("$.data[0].rootSlug").value("policies"))
                    .andExpect(jsonPath("$.data[0].updatedAt").isNotEmpty())
                    .andExpect(jsonPath("$.data[0].updatedByName").isNotEmpty())
                    .andExpect(jsonPath("$.data[1].path").value("policies/backup"))
                    .andExpect(jsonPath("$.data[2].path").value("policies"));
        }

        @Test
        void getRecent_limit_capsTheList() throws Exception {
            String rootId = createdId(admin, null, "policies");
            createdChain(admin, rootId, "a", "b");

            send(get(PAGES + "/recent").param("limit", "2"), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].path").value("policies/a/b"));
        }

        @Test
        void getRecent_hiddenRootAndDeletedPage_areLeftOut() throws Exception {
            String visibleId = createdId(admin, null, "policies");
            String deletedId = createdId(admin, visibleId, "old");
            createdId(admin, null, "secret");
            deletePage(admin, deletedId).andExpect(status().isOk());
            grant(visibleId, guest, "VIEW");

            send(get(PAGES + "/recent"), guest, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(visibleId));
        }

        @Test
        void getRecent_limitOutOfRange_returns422() throws Exception {
            send(get(PAGES + "/recent").param("limit", "0"), admin, null)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"))
                    .andExpect(jsonPath("$.error.errorValues.limit").exists());
            send(get(PAGES + "/recent").param("limit", "101"), admin, null)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.errorValues.limit").exists());
        }

        @Test
        void getRecent_limitAtTheBounds_succeeds() throws Exception {
            String rootId = createdId(admin, null, "policies");
            createdId(admin, rootId, "access");

            send(get(PAGES + "/recent").param("limit", "1"), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
            send(get(PAGES + "/recent").param("limit", "100"), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        void getRecent_withoutLimit_listsTwenty() throws Exception {
            String rootId = createdId(admin, null, "policies");
            for (int i = 0; i < 20; i++) {
                createdId(admin, rootId, "page-" + i);
            }

            send(get(PAGES + "/recent"), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(20));
        }

        @Test
        void getRecent_limitNotANumberOrUnknownLocale_returns422() throws Exception {
            send(get(PAGES + "/recent").param("limit", "many"), admin, null)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.errorValues.limit").exists());
            send(get(PAGES + "/recent").param("locale", "DE"), admin, null)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.errorValues.locale").exists());
        }

        @Test
        void getRecent_locale_picksThatTitle() throws Exception {
            String rootId = createdBilingualRoot("isms");

            send(get(PAGES + "/recent").param("locale", "EN"), admin, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(rootId))
                    .andExpect(jsonPath("$.data[0].title").value("Title isms"));
            send(get(PAGES + "/recent").param("locale", "TR"), admin, null)
                    .andExpect(jsonPath("$.data[0].title").value("Baslik isms"));
        }
    }
}
