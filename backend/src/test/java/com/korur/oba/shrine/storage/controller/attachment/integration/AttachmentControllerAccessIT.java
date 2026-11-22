package com.korur.oba.shrine.storage.controller.attachment.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The attachment endpoints accept only the entity types that have access rules: the removed organization, user and
 * invoice types are rejected as unknown values. Ticket and chat attachments keep working for the users their rules
 * allow, every stored attachment carries the request's organization, an entity of another organization than the
 * request header names is refused before any file or row changes, an attachment of another organization is not
 * found, and a guest reads but never writes.
 */
class AttachmentControllerAccessIT extends AbstractTenantIT {

    private static final String ORGANIZATION_HEADER = "X-Organization-ID";
    private static final String ORGANIZATION_MISMATCH = "APP_SEC_002";
    private static final String HEADER_MISSING = "GEN_API_007";
    private static final String ROLE_TOO_LOW = "ORG_SEC_003";
    private static final String APP_ACCESS_DENIED = "APP_SEC_001";
    private static final String FILE_NOT_FOUND = "STG_NOT_001";
    private static final List<String> REMOVED_TYPES = List.of("ORGANIZATION", "USER", "INVOICE");

    private Tenant tenantA;
    private Tenant tenantB;
    private Person personA;
    private Person personB;
    private String boardIdA;

    @MockitoSpyBean
    private AzureBlobStorageService azureBlobStorageService;

    @BeforeEach
    void setUp() throws Exception {
        tenantA = newTenant();
        tenantB = newTenant();
        personA = signIn(tenantA, "attach");
        personB = signIn(tenantB, "attachb");
    }

    private static MockMultipartFile file(String partName, String fileName, String content) {
        return new MockMultipartFile(partName, fileName, MediaType.APPLICATION_PDF_VALUE, content.getBytes());
    }

    private static MockMultipartFile json(String partName, String body) {
        return new MockMultipartFile(partName, "", MediaType.APPLICATION_JSON_VALUE, body.getBytes());
    }

    private static String uploadMetadata(String fileName, String entityType, String entityId) {
        return "[{\"fileName\":\"" + fileName + "\",\"entityType\":\"" + entityType
                + "\",\"entityId\":\"" + entityId + "\"}]";
    }

    private static String existsMetadata(String fileName, String entityType) {
        return "{\"fileName\":\"" + fileName + "\",\"entityType\":\"" + entityType + "\"}";
    }

    private String createTicket() throws Exception {
        return createTicket(tenantA, personA);
    }

    private String createTicket(Tenant tenant, Person person) throws Exception {
        String prefix = "A" + uniqueSuffix().substring(0, 8).toUpperCase();
        String boardId = read(mockMvc.perform(in(tenant, post("/kazan/boards"), person.jwt(),
                "{\"name\":\"Board " + prefix + "\",\"prefix\":\"" + prefix + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        if (tenant == tenantA) {
            boardIdA = boardId;
        }
        String sprintId = read(mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/sprints"),
                person.jwt(), "{\"name\":\"Sprint\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        return read(mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/tickets"), person.jwt(),
                "{\"title\":\"Ticket\",\"type\":\"FEATURE\",\"priority\":\"MEDIUM\",\"status\":\"TODO\","
                        + "\"missionRank\":\"M\",\"sprintId\":\"" + sprintId + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
    }

    private UUID insertTicketAttachment(Tenant tenant, String ticketId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO shrine.attachments (url, file_name, content_type, file_size, entity_type, entity_id,
                                                organization_id)
                VALUES (?, 'spec.pdf', 'application/pdf', 10,
                        'TICKET'::shrine_storage_entity_type_enum, ?::uuid, ?)
                RETURNING id
                """, UUID.class, "https://blob.invalid/" + uniqueSuffix() + ".pdf", ticketId,
                tenant.organization().getId());
    }

    /** A guest of tenant A who is a viewer of the board of tenant A's ticket. */
    private Person guestViewerOfBoardA() throws Exception {
        Person guest = signIn("kc-" + uniqueSuffix(), "guest" + uniqueSuffix().substring(0, 6) + "@" + tenantA.domain());
        createMember(tenantA.organization(), guest.user(), OrganizationMemberRole.GUEST, OrganizationMemberStatus.ACTIVE);
        jdbcTemplate.update("INSERT INTO kazan.board_members (id, role, board_id, user_id) "
                + "VALUES (gen_random_uuid(), 'VIEWER'::kazan_board_member_role_enum, ?::uuid, ?::uuid)",
                boardIdA, guest.userId());
        return guest;
    }

    @Test
    void listAttachments_removedEntityType_returns400() throws Exception {
        for (String entityType : REMOVED_TYPES) {
            mockMvc.perform(get("/storage/attachments").param("entityType", entityType)
                            .param("entityId", tenantB.id()).with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void uploadAttachments_removedEntityType_returns400AndStoresNothing() throws Exception {
        for (String entityType : REMOVED_TYPES) {
            String entityId = UUID.randomUUID().toString();
            mockMvc.perform(multipart("/storage/attachments")
                            .file(json("metadata", uploadMetadata("doc.pdf", entityType, entityId)))
                            .file(file("files", "doc.pdf", "upload " + entityType))
                            .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                    .andExpect(status().isBadRequest());

            assertEquals(0L, count("SELECT count(*) FROM shrine.attachments WHERE entity_id = ?::uuid", entityId));
        }
        verify(azureBlobStorageService, never()).uploadFile(any(), any());
    }

    @Test
    void checkAttachmentExists_removedEntityType_returns400() throws Exception {
        for (String entityType : REMOVED_TYPES) {
            mockMvc.perform(multipart("/storage/attachments/exists")
                            .file(json("metadata", existsMetadata("doc.pdf", entityType)))
                            .file(file("file", "doc.pdf", "probe " + entityType))
                            .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void uploadAttachments_ticket_storesTheRequestOrganization() throws Exception {
        String ticketId = createTicket();

        String attachmentId = read(mockMvc.perform(multipart("/storage/attachments")
                        .file(json("metadata", uploadMetadata("spec.pdf", "TICKET", ticketId)))
                        .file(file("files", "spec.pdf", "organization " + uniqueSuffix()))
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isCreated()).andReturn(), "$.data[0].id");

        assertEquals(tenantA.organization().getId(), jdbcTemplate.queryForObject(
                "SELECT organization_id FROM shrine.attachments WHERE id = ?::uuid", UUID.class, attachmentId));
    }

    @Test
    void checkAttachmentExists_sameTicketFileOnlyInAnotherOrganization_returnsFalse() throws Exception {
        String ticketIdB = createTicket(tenantB, personB);
        String content = "other organization file " + uniqueSuffix();
        mockMvc.perform(multipart("/storage/attachments")
                        .file(json("metadata", uploadMetadata("spec.pdf", "TICKET", ticketIdB)))
                        .file(file("files", "spec.pdf", content))
                        .with(personB.jwt()).header(ORGANIZATION_HEADER, tenantB.id()))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/storage/attachments/exists")
                        .file(json("metadata", existsMetadata("spec.pdf", "TICKET")))
                        .file(file("file", "spec.pdf", content))
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(false));
    }

    @Test
    void ticketAttachments_boardMember_uploadsListsChecksAndDeletes() throws Exception {
        String ticketId = createTicket();
        String content = "ticket file " + uniqueSuffix();

        String attachmentId = read(mockMvc.perform(multipart("/storage/attachments")
                        .file(json("metadata", uploadMetadata("spec.pdf", "TICKET", ticketId)))
                        .file(file("files", "spec.pdf", content))
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isCreated()).andReturn(), "$.data[0].id");

        mockMvc.perform(get("/storage/attachments").param("entityType", "TICKET").param("entityId", ticketId)
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(attachmentId));

        mockMvc.perform(multipart("/storage/attachments/exists")
                        .file(json("metadata", existsMetadata("spec.pdf", "TICKET")))
                        .file(file("file", "spec.pdf", content))
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true));

        mockMvc.perform(delete("/storage/attachments/" + attachmentId).with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isOk());

        assertEquals(0L, count("SELECT count(*) FROM shrine.attachments WHERE id = ?::uuid", attachmentId));
    }

    @Test
    void everyEndpoint_missingOrganizationHeader_returns400() throws Exception {
        String ticketId = createTicket();
        UUID attachmentId = insertTicketAttachment(tenantA, ticketId);

        mockMvc.perform(get("/storage/attachments").param("entityType", "TICKET").param("entityId", ticketId)
                        .with(personA.jwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value(HEADER_MISSING));
        mockMvc.perform(multipart("/storage/attachments")
                        .file(json("metadata", uploadMetadata("doc.pdf", "TICKET", ticketId)))
                        .file(file("files", "doc.pdf", "no header")).with(personA.jwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value(HEADER_MISSING));
        mockMvc.perform(multipart("/storage/attachments/exists")
                        .file(json("metadata", existsMetadata("doc.pdf", "TICKET")))
                        .file(file("file", "doc.pdf", "no header")).with(personA.jwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value(HEADER_MISSING));
        mockMvc.perform(delete("/storage/attachments/" + attachmentId).with(personA.jwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value(HEADER_MISSING));

        assertEquals(1L, count("SELECT count(*) FROM shrine.attachments WHERE id = ?", attachmentId));
    }

    @Test
    void listAttachments_ticketOfAnotherOrganization_returns403OrganizationMismatch() throws Exception {
        String ticketIdB = createTicket(tenantB, personB);

        mockMvc.perform(get("/storage/attachments").param("entityType", "TICKET").param("entityId", ticketIdB)
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ORGANIZATION_MISMATCH));
    }

    @Test
    void uploadAttachments_ticketOfAnotherOrganization_returns403AndWritesNoBlobNorRow() throws Exception {
        String ticketIdB = createTicket(tenantB, personB);

        mockMvc.perform(multipart("/storage/attachments")
                        .file(json("metadata", uploadMetadata("doc.pdf", "TICKET", ticketIdB)))
                        .file(file("files", "doc.pdf", "cross organization " + uniqueSuffix()))
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ORGANIZATION_MISMATCH));

        assertEquals(0L, count("SELECT count(*) FROM shrine.attachments WHERE entity_id = ?::uuid", ticketIdB));
        verify(azureBlobStorageService, never()).uploadFile(any(), any());
    }

    @Test
    void uploadAttachments_batchWithOneTicketOfAnotherOrganization_isRefusedAsAWhole() throws Exception {
        String ownTicket = createTicket();
        String ticketIdB = createTicket(tenantB, personB);
        String metadata = "[{\"fileName\":\"a.pdf\",\"entityType\":\"TICKET\",\"entityId\":\"" + ownTicket
                + "\"},{\"fileName\":\"b.pdf\",\"entityType\":\"TICKET\",\"entityId\":\"" + ticketIdB + "\"}]";

        mockMvc.perform(multipart("/storage/attachments").file(json("metadata", metadata))
                        .file(file("files", "a.pdf", "batch a " + uniqueSuffix()))
                        .file(file("files", "b.pdf", "batch b " + uniqueSuffix()))
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ORGANIZATION_MISMATCH));

        assertEquals(0L, count("SELECT count(*) FROM shrine.attachments WHERE entity_id IN (?::uuid, ?::uuid)",
                ownTicket, ticketIdB));
        verify(azureBlobStorageService, never()).uploadFile(any(), any());
    }

    @Test
    void deleteAttachment_ticketAttachmentOfAnotherOrganization_returns404KeepsRowAndBlob() throws Exception {
        String ticketIdB = createTicket(tenantB, personB);
        UUID attachmentId = insertTicketAttachment(tenantB, ticketIdB);

        // Not found rather than forbidden, so the attachment's existence is not revealed.
        mockMvc.perform(delete("/storage/attachments/" + attachmentId)
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(FILE_NOT_FOUND));

        assertEquals(1L, count("SELECT count(*) FROM shrine.attachments WHERE id = ?", attachmentId));
        verify(azureBlobStorageService, never()).deleteFile(any());
    }

    @Test
    void ticketAttachments_guestOfTheBoard_listsButCannotUploadOrDelete() throws Exception {
        String ticketId = createTicket();
        UUID attachmentId = insertTicketAttachment(tenantA, ticketId);
        Person guest = guestViewerOfBoardA();

        mockMvc.perform(get("/storage/attachments").param("entityType", "TICKET").param("entityId", ticketId)
                        .with(guest.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(multipart("/storage/attachments")
                        .file(json("metadata", uploadMetadata("doc.pdf", "TICKET", ticketId)))
                        .file(file("files", "doc.pdf", "guest upload " + uniqueSuffix()))
                        .with(guest.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(ROLE_TOO_LOW));
        mockMvc.perform(delete("/storage/attachments/" + attachmentId)
                        .with(guest.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(ROLE_TOO_LOW));

        assertEquals(1L, count("SELECT count(*) FROM shrine.attachments WHERE entity_id = ?::uuid", ticketId));
        verify(azureBlobStorageService, never()).uploadFile(any(), any());
        verify(azureBlobStorageService, never()).deleteFile(any());
    }

    @Test
    void ticketAttachments_organizationWithoutKazan_returns403AppAccessDenied() throws Exception {
        String ticketId = createTicket();
        jdbcTemplate.update("UPDATE shrine.organization_app_grants SET deleted = TRUE "
                + "WHERE organization_id = ? AND app = 'KAZAN'::shrine_app_grant_app_enum",
                tenantA.organization().getId());

        mockMvc.perform(get("/storage/attachments").param("entityType", "TICKET").param("entityId", ticketId)
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(APP_ACCESS_DENIED));
    }

    @Test
    void chatMessage_sendWithFile_storesTheFileAndListsIt() throws Exception {
        String channelId = read(mockMvc.perform(in(tenantA, post("/kazan/chat/channels"),
                        jwtOf(tenantA.admin().getUser()),
                        "{\"name\":\"general" + uniqueSuffix() + "\",\"visibility\":\"PUBLIC\",\"purpose\":\"GENERAL\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");

        String messageId = read(mockMvc.perform(multipart("/kazan/chat/messages")
                        .file(json("message", "{\"content\":\"With file\",\"channelId\":\"" + channelId
                                + "\",\"clientNonce\":\"" + uniqueSuffix() + "\"}"))
                        .file(file("files", "note.pdf", "chat file " + uniqueSuffix()))
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        mockMvc.perform(get("/storage/attachments").param("entityType", "CHAT_MESSAGE").param("entityId", messageId)
                        .with(personA.jwt()).header(ORGANIZATION_HEADER, tenantA.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        assertEquals(1L, count("SELECT count(*) FROM shrine.attachments WHERE entity_id = ?::uuid", messageId));
    }
}
