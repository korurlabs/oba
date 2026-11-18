package com.korur.oba.shrine.storage.controller.attachment.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.support.AbstractKalkanIT;
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The central resource access check guards the attachments of Kalkan pages, so it is called directly here
 * with real memberships, grants and soft-deleted pages.
 */
class KalkanPageAttachmentAccessIT extends AbstractKalkanIT {

    @Autowired
    private ResourceAccessQueryService access;

    @MockitoSpyBean
    private AzureBlobStorageService azureBlobStorageService;

    private void verifyAccess(User caller, UUID pageId, ResourceAccessAction action) {
        access.verifyResourceAccess(caller, organization.getId(), ResourceAccessType.KALKAN_PAGE, pageId, action);
    }

    private void grant(String rootId, User user, String level) throws Exception {
        send(put(PAGES + "/{id}/permissions", rootId), owner,
                "[{\"userId\":\"" + admin.getId() + "\",\"level\":\"MANAGE\"},{\"userId\":\"" + user.getId()
                        + "\",\"level\":\"" + level + "\"}]").andExpect(status().isOk());
    }

    @Test
    void read_viewerOfTheRoot_passesForTheRootAndItsChild() throws Exception {
        String rootId = createdId(admin, null, "isms");
        String childId = createdId(admin, rootId, "policies");
        grant(rootId, guest, "VIEW");

        assertDoesNotThrow(() -> verifyAccess(guest, UUID.fromString(rootId), ResourceAccessAction.READ));
        assertDoesNotThrow(() -> verifyAccess(guest, UUID.fromString(childId), ResourceAccessAction.READ));
    }

    @Test
    void read_memberWithoutGrant_isHiddenAs404() throws Exception {
        String rootId = createdId(admin, null, "isms");

        NotFoundException thrown = assertThrows(NotFoundException.class,
                () -> verifyAccess(member, UUID.fromString(rootId), ResourceAccessAction.READ));

        assertEquals("KLK_NOT_001", thrown.getErrorCode());
    }

    @Test
    void read_userOfAnotherOrganization_isRefusedByTheOrganizationCheck() throws Exception {
        String rootId = createdId(admin, null, "isms");
        User outsider = newMember(createOrganization(OrganizationStatus.ACTIVE), OrganizationMemberRole.OWNER);

        // The platform's organization check answers 404 for a non-member, so the page's existence is not revealed.
        assertThrows(NotFoundException.class, () -> verifyAccess(outsider, UUID.fromString(rootId), ResourceAccessAction.READ));
    }

    @Test
    void write_viewer_isRefusedAndEditorPasses() throws Exception {
        String rootId = createdId(admin, null, "isms");
        grant(rootId, member, "VIEW");

        assertThrows(ForbiddenException.class, () -> verifyAccess(member, UUID.fromString(rootId), ResourceAccessAction.WRITE));

        grant(rootId, member, "EDIT");

        assertDoesNotThrow(() -> verifyAccess(member, UUID.fromString(rootId), ResourceAccessAction.WRITE));
    }

    @Test
    void readAndWrite_organizationRevokedKalkan_refuseEveryoneButTheOwner() throws Exception {
        String rootId = createdId(admin, null, "isms");
        grant(rootId, member, "EDIT");
        jdbcTemplate.update("UPDATE shrine.organization_app_grants SET deleted = TRUE "
                + "WHERE organization_id = ? AND app = 'KALKAN'::shrine_app_grant_app_enum", organization.getId());
        UUID pageId = UUID.fromString(rootId);

        ForbiddenException readRefused = assertThrows(ForbiddenException.class, () -> verifyAccess(member, pageId, ResourceAccessAction.READ));
        ForbiddenException writeRefused = assertThrows(ForbiddenException.class,
                () -> verifyAccess(member, pageId, ResourceAccessAction.WRITE));

        assertEquals("APP_SEC_001", readRefused.getErrorCode());
        assertEquals("APP_SEC_001", writeRefused.getErrorCode());
        assertDoesNotThrow(() -> verifyAccess(owner, pageId, ResourceAccessAction.WRITE));
    }

    @Test
    void write_guestWithViewOnly_isRefused() throws Exception {
        String rootId = createdId(admin, null, "isms");
        grant(rootId, guest, "VIEW");

        assertThrows(ForbiddenException.class, () -> verifyAccess(guest, UUID.fromString(rootId), ResourceAccessAction.WRITE));
    }

    @Test
    void read_deletedPage_answers404UntilRestored() throws Exception {
        String rootId = createdId(admin, null, "isms");
        String childId = createdId(admin, rootId, "policies");
        deletePage(admin, childId).andExpect(status().isOk());

        assertThrows(NotFoundException.class, () -> verifyAccess(owner, UUID.fromString(childId), ResourceAccessAction.READ));

        restorePage(admin, childId).andExpect(status().isOk());

        assertDoesNotThrow(() -> verifyAccess(owner, UUID.fromString(childId), ResourceAccessAction.READ));
    }

    @Test
    void delete_owner_isStillRefused() throws Exception {
        String rootId = createdId(admin, null, "isms");

        ForbiddenException thrown = assertThrows(ForbiddenException.class,
                () -> verifyAccess(owner, UUID.fromString(rootId), ResourceAccessAction.DELETE));

        assertEquals("KLK_SEC_002", thrown.getErrorCode());
    }

    @Test
    void read_headerOrganizationDiffersFromThePageOrganization_isRefusedWithAppSec002() throws Exception {
        String rootId = createdId(admin, null, "isms");
        Organization other = createOrganization(OrganizationStatus.ACTIVE);
        User memberOfOther = newMember(other, OrganizationMemberRole.OWNER);

        ForbiddenException thrown = assertThrows(ForbiddenException.class, () -> access.verifyResourceAccess(
                memberOfOther, other.getId(), ResourceAccessType.KALKAN_PAGE, UUID.fromString(rootId),
                ResourceAccessAction.READ));

        assertEquals("APP_SEC_002", thrown.getErrorCode());
    }

    @Test
    void deleteEndpoint_owner_isRefusedWithKlkSec002AndKeepsRowAndBlob() throws Exception {
        String rootId = createdId(admin, null, "isms");
        UUID attachmentId = jdbcTemplate.queryForObject("""
                INSERT INTO shrine.attachments (url, file_name, content_type, file_size, entity_type, entity_id)
                VALUES (?, 'evidence.pdf', 'application/pdf', 10,
                        'KALKAN_PAGE'::shrine_storage_entity_type_enum, ?::uuid)
                RETURNING id
                """, UUID.class, "https://blob.invalid/" + uniqueSuffix() + ".pdf", rootId);

        send(delete("/storage/attachments/{id}", attachmentId), owner, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KLK_SEC_002"));

        assertEquals(1L, count("SELECT count(*) FROM shrine.attachments WHERE id = ?", attachmentId));
        verify(azureBlobStorageService, never()).deleteFile(any());
    }
}
