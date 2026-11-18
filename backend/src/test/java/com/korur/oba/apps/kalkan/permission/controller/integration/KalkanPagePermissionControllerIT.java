package com.korur.oba.apps.kalkan.permission.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.support.AbstractKalkanIT;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KalkanPagePermissionControllerIT extends AbstractKalkanIT {

    private String rootId;

    @BeforeEach
    void setUpRoot() throws Exception {
        rootId = createdId(admin, null, "isms");
    }

    private static String grantOf(User user, String level) {
        return "{\"userId\":\"" + user.getId() + "\",\"level\":\"" + level + "\"}";
    }

    private ResultActions replace(User caller, String pageId, String... grants) throws Exception {
        return send(put(PAGES + "/{id}/permissions", pageId), caller, "[" + String.join(",", grants) + "]");
    }

    private ResultActions list(User caller, String pageId) throws Exception {
        return send(get(PAGES + "/{id}/permissions", pageId), caller, null);
    }

    private long liveRows(User user) {
        return count("SELECT count(*) FROM kalkan.page_permissions WHERE page_id = ?::uuid AND user_id = ?::uuid "
                + "AND deleted = FALSE", rootId, user.getId());
    }

    private long allRows(User user) {
        return count("SELECT count(*) FROM kalkan.page_permissions WHERE page_id = ?::uuid AND user_id = ?::uuid",
                rootId, user.getId());
    }

    @Nested
    class Read {

        @Test
        void getPermissions_asManager_listsCreatorFirstWithDisplayName() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());

            list(admin, rootId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].userId").value(admin.getId().toString()))
                    .andExpect(jsonPath("$.data[0].level").value("MANAGE"))
                    .andExpect(jsonPath("$.data[0].displayName").value(admin.getEmail()))
                    .andExpect(jsonPath("$.data[1].level").value("EDIT"));
        }

        @Test
        void getPermissions_asOwnerWithoutGrant_succeeds() throws Exception {
            list(owner, rootId).andExpect(status().isOk());
        }

        @Test
        void getPermissions_memberWithoutGrant_returns404() throws Exception {
            list(member, rootId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void getPermissions_memberWithEditOnly_returns403LevelTooLow() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());

            list(member, rootId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("KLK_SEC_001"))
                    .andExpect(jsonPath("$.error.errorValues.level").value("MANAGE"));
        }

        @Test
        void getPermissions_guest_returns403FromGate() throws Exception {
            list(guest, rootId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        }

        @Test
        void getPermissions_childPage_returns422() throws Exception {
            String childId = createdId(admin, rootId, "policies");

            list(admin, childId)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_006"));
        }

        @Test
        void getPermissions_childPageWithoutAnyLevel_returns404BeforeRevealingTheTree() throws Exception {
            String childId = createdId(admin, rootId, "policies");

            list(member, childId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }

        @Test
        void getPermissions_otherOrganization_returns404() throws Exception {
            Organization other = createOrganization(OrganizationStatus.ACTIVE);
            User outsider = newMember(other, OrganizationMemberRole.OWNER);

            send(get(PAGES + "/{id}/permissions", rootId), outsider, other.getId(), null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KLK_NOT_001"));
        }
    }

    @Nested
    class Replace {

        @Test
        void putPermissions_sameLevel_keepsTheRowUntouched() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());
            String rowId = jdbcTemplate.queryForObject("SELECT id::text FROM kalkan.page_permissions WHERE page_id = "
                    + "?::uuid AND user_id = ?::uuid", String.class, rootId, member.getId());

            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());

            assertEquals(1, allRows(member));
            assertEquals(rowId, jdbcTemplate.queryForObject("SELECT id::text FROM kalkan.page_permissions WHERE "
                    + "page_id = ?::uuid AND user_id = ?::uuid", String.class, rootId, member.getId()));
        }

        @Test
        void putPermissions_levelChanged_softDeletesOldRowAndInsertsNewOne() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());

            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "VIEW"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));

            assertEquals(2, allRows(member));
            assertEquals(1, liveRows(member));
            assertEquals(1, count("SELECT count(*) FROM kalkan.page_permissions WHERE page_id = ?::uuid AND user_id = "
                    + "?::uuid AND deleted = FALSE AND level::text = 'VIEW'", rootId, member.getId()));
        }

        @Test
        void putPermissions_subjectRemoved_softDeletesRowWithRevoker() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());

            replace(owner, rootId, grantOf(admin, "MANAGE")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));

            assertEquals(0, liveRows(member));
            assertEquals(1, count("SELECT count(*) FROM kalkan.page_permissions WHERE page_id = ?::uuid AND user_id = "
                    + "?::uuid AND deleted = TRUE AND updated_by_id = ?::uuid AND created_by_id = ?::uuid",
                    rootId, member.getId(), owner.getId(), admin.getId()));
            getPage(member, rootId).andExpect(status().isNotFound());
        }

        @Test
        void putPermissions_reGrantedSubject_getsANewRowAndKeepsTheEarlierPeriod() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());
            replace(admin, rootId, grantOf(admin, "MANAGE")).andExpect(status().isOk());

            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());

            assertEquals(2, allRows(member));
            assertEquals(1, liveRows(member));
        }

        @Test
        void putPermissions_emptyList_revokesEveryone() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());

            replace(owner, rootId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));

            assertEquals(0, liveRows(admin));
            assertEquals(0, liveRows(member));
        }

        @Test
        void putPermissions_newGrant_recordsGranterInBothColumns() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());

            assertEquals(1, count("SELECT count(*) FROM kalkan.page_permissions WHERE page_id = ?::uuid AND user_id = "
                    + "?::uuid AND created_by_id = ?::uuid AND updated_by_id = ?::uuid", rootId, member.getId(),
                    admin.getId(), admin.getId()));
        }

        @Test
        void putPermissions_groupId_returns422GroupsNotSupported() throws Exception {
            replace(admin, rootId, "{\"groupId\":\"" + UUID.randomUUID() + "\",\"level\":\"VIEW\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_002"))
                    .andExpect(jsonPath("$.error.errorValues.reason").value("GROUPS_NOT_SUPPORTED"));
        }

        @Test
        void putPermissions_noSubject_returns422() throws Exception {
            replace(admin, rootId, "{\"level\":\"VIEW\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.errorValues.reason").value("NO_SUBJECT"));
        }

        @Test
        void putPermissions_missingLevel_returns422Validation() throws Exception {
            replace(admin, rootId, "{\"userId\":\"" + member.getId() + "\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }

        @Test
        void putPermissions_duplicateSubject_returns422() throws Exception {
            replace(admin, rootId, grantOf(member, "VIEW"), grantOf(member, "EDIT"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.errorValues.reason").value("DUPLICATE_SUBJECT"));
        }

        @Test
        void putPermissions_memberOfAnotherOrganization_returns422NotActiveMember() throws Exception {
            User foreign = newMember(createOrganization(OrganizationStatus.ACTIVE), OrganizationMemberRole.MEMBER);

            replace(admin, rootId, grantOf(foreign, "VIEW"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_002"))
                    .andExpect(jsonPath("$.error.errorValues.reason").value("NOT_ACTIVE_MEMBER"));
        }

        @Test
        void putPermissions_disabledMember_returns422NotActiveMember() throws Exception {
            User disabled = createUser("kc-" + uniqueSuffix(), "d" + uniqueSuffix() + "@" + uniqueDomain());
            createMember(organization, disabled, OrganizationMemberRole.MEMBER, OrganizationMemberStatus.DISABLED);

            replace(admin, rootId, grantOf(disabled, "VIEW"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.errorValues.reason").value("NOT_ACTIVE_MEMBER"));
        }

        @Test
        void putPermissions_guestAboveView_returns422() throws Exception {
            replace(admin, rootId, grantOf(guest, "EDIT"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_003"))
                    .andExpect(jsonPath("$.error.errorValues.level").value("EDIT"));
        }

        @Test
        void putPermissions_guestAtView_succeeds() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(guest, "VIEW")).andExpect(status().isOk());

            getPage(guest, rootId).andExpect(status().isOk());
        }

        @Test
        void putPermissions_invalidEntryChangesNothing() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());

            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(guest, "EDIT"))
                    .andExpect(status().isUnprocessableEntity());

            assertEquals(1, liveRows(member));
            assertEquals(0, allRows(guest));
        }

        @Test
        void getPage_memberDemotedToGuestAfterGrant_isCappedAtView() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());
            jdbcTemplate.update("UPDATE shrine.organization_members SET role = 'GUEST' WHERE user_id = ?::uuid", member.getId());

            getPage(member, rootId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.level").value("VIEW"));
        }

        @Test
        void putPermissions_childPage_returns422() throws Exception {
            String childId = createdId(admin, rootId, "policies");

            replace(admin, childId, grantOf(member, "VIEW"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KLK_VAL_006"));
        }

        @Test
        void putPermissions_memberWithEditOnly_returns403() throws Exception {
            replace(admin, rootId, grantOf(admin, "MANAGE"), grantOf(member, "EDIT")).andExpect(status().isOk());

            replace(member, rootId, grantOf(member, "MANAGE"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("KLK_SEC_001"));
            assertEquals(1, count("SELECT count(*) FROM kalkan.page_permissions WHERE page_id = ?::uuid AND user_id = "
                    + "?::uuid AND deleted = FALSE AND level::text = 'EDIT'", rootId, member.getId()));
        }
    }
}
