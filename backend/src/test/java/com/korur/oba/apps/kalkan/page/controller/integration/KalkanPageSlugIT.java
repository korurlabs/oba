package com.korur.oba.apps.kalkan.page.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.repository.KalkanPageRepository;
import com.korur.oba.apps.kalkan.support.AbstractKalkanIT;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slug uniqueness among live pages: per parent, and at the top level where the parent is null.
 * Runs on the real database because the answer comes from the repository's exists queries and the
 * partial unique indexes behind them.
 */
class KalkanPageSlugIT extends AbstractKalkanIT {

    @Autowired
    private KalkanPageRepository kalkanPageRepository;

    @Nested
    class ExistsQueries {

        @Test
        void existsRootSlug_noExclusion_findsLiveRoot() throws Exception {
            createdId(admin, null, "isms");

            assertTrue(kalkanPageRepository.existsRootSlug(organization.getId(), "isms", null));
        }

        @Test
        void existsRootSlug_excludingTheOwnPage_returnsFalse() throws Exception {
            String rootId = createdId(admin, null, "isms");

            assertFalse(kalkanPageRepository.existsRootSlug(organization.getId(), "isms", UUID.fromString(rootId)));
        }

        @Test
        void existsRootSlug_excludingAnotherPage_stillFindsRoot() throws Exception {
            createdId(admin, null, "isms");

            assertTrue(kalkanPageRepository.existsRootSlug(organization.getId(), "isms", UUID.randomUUID()));
        }

        @Test
        void existsRootSlug_slugOfAnotherOrganization_returnsFalse() throws Exception {
            createdId(admin, null, "isms");

            assertFalse(kalkanPageRepository.existsRootSlug(UUID.randomUUID(), "isms", null));
        }

        @Test
        void existsChildSlug_noExclusion_findsLiveChild() throws Exception {
            String rootId = createdId(admin, null, "isms");
            createdId(admin, rootId, "policies");

            assertTrue(kalkanPageRepository.existsChildSlug(UUID.fromString(rootId), "policies", null));
        }

        @Test
        void existsChildSlug_excludingTheOwnPage_returnsFalse() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "policies");

            assertFalse(kalkanPageRepository.existsChildSlug(UUID.fromString(rootId), "policies",
                    UUID.fromString(childId)));
        }

        @Test
        void existsChildSlug_excludingAnotherPage_stillFindsChild() throws Exception {
            String rootId = createdId(admin, null, "isms");
            createdId(admin, rootId, "policies");

            assertTrue(kalkanPageRepository.existsChildSlug(UUID.fromString(rootId), "policies", UUID.randomUUID()));
        }

        @Test
        void existsChildSlug_deletedChild_returnsFalse() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "policies");
            deletePage(admin, childId).andExpect(status().isOk());

            assertFalse(kalkanPageRepository.existsChildSlug(UUID.fromString(rootId), "policies", null));
        }
    }

    @Nested
    class Create {

        @Test
        void createRoot_slugOfLiveRoot_returns409() throws Exception {
            createdId(admin, null, "isms");

            createPage(admin, null, "isms")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_001"));
        }

        @Test
        void createRoot_slugOfDeletedRoot_succeeds() throws Exception {
            String rootId = createdId(admin, null, "isms");
            deletePage(admin, rootId).andExpect(status().isOk());

            createPage(admin, null, "isms").andExpect(status().isCreated());
        }

        @Test
        void createChild_slugOfLiveSibling_returns409() throws Exception {
            String rootId = createdId(admin, null, "isms");
            createdId(admin, rootId, "policies");

            createPage(admin, rootId, "policies")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_001"));
        }

        @Test
        void createChild_sameSlugUnderAnotherParent_succeeds() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String firstId = createdId(admin, rootId, "controls");
            String secondId = createdId(admin, rootId, "risks");
            createdId(admin, firstId, "overview");

            createPage(admin, secondId, "overview").andExpect(status().isCreated());
        }

        @Test
        void createChild_slugOfTheRootNamedLikeIt_succeeds() throws Exception {
            String rootId = createdId(admin, null, "isms");

            createPage(admin, rootId, "isms").andExpect(status().isCreated());
        }
    }

    @Nested
    class Update {

        @Test
        void updatePage_renameRootToLiveRootSlug_returns409() throws Exception {
            createdId(admin, null, "isms");
            String otherId = createdId(admin, null, "handbook");

            updatePage(admin, otherId, "{\"slug\":\"isms\",\"version\":0}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_001"));
        }

        @Test
        void updatePage_renameChildToSiblingSlug_returns409() throws Exception {
            String rootId = createdId(admin, null, "isms");
            createdId(admin, rootId, "policies");
            String otherId = createdId(admin, rootId, "controls");

            updatePage(admin, otherId, "{\"slug\":\"policies\",\"version\":0}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_001"));
        }

        @Test
        void updatePage_sameSlugAsItself_succeeds() throws Exception {
            String rootId = createdId(admin, null, "isms");

            updatePage(admin, rootId, "{\"slug\":\"isms\",\"compliance\":true,\"version\":0}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.slug").value("isms"));
        }
    }

    @Nested
    class Move {

        @Test
        void movePage_intoParentHoldingTheSlug_returns409() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String firstId = createdId(admin, rootId, "controls");
            String secondId = createdId(admin, rootId, "risks");
            createdId(admin, firstId, "overview");
            String movedId = createdId(admin, secondId, "overview");

            movePage(admin, movedId, firstId, 0, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_001"));
        }

        @Test
        void movePage_reorderWithinSameParent_succeeds() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "policies");

            movePage(admin, childId, rootId, 5, 0)
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class Restore {

        @Test
        void restorePage_rootSlugTakenMeanwhile_returns409() throws Exception {
            String rootId = createdId(admin, null, "isms");
            deletePage(admin, rootId).andExpect(status().isOk());
            createdId(admin, null, "isms");

            restorePage(admin, rootId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_001"));
        }

        @Test
        void restorePage_childSlugTakenMeanwhile_returns409() throws Exception {
            String rootId = createdId(admin, null, "isms");
            String childId = createdId(admin, rootId, "policies");
            deletePage(admin, childId).andExpect(status().isOk());
            createdId(admin, rootId, "policies");

            restorePage(admin, childId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KLK_STA_001"));
        }
    }
}
