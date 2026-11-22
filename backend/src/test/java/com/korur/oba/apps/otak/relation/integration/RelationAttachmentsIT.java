package com.korur.oba.apps.otak.relation.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.nio.charset.StandardCharsets;

// 4. Internal
import com.korur.oba.apps.otak.support.AbstractOtakDocumentsIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves GET /otak/relations/{relationId}/attachments through the real security chain: the
 * relation's own files and its mails' files in one list, a document snapshot carrying its
 * document id, the source and outreach title set per file, and another organization's
 * relation answering 404.
 */
class RelationAttachmentsIT extends AbstractOtakDocumentsIT {

    private static final String ATTACHMENTS_PATH = "/storage/attachments";
    private static final String DOCUMENTS_PATH = "/documents";

    private Tenant tenant;
    private Tenant otherTenant;
    private Person admin;
    private Person member;
    private Person otherAdmin;
    private String relationId;
    private String mailId;
    private String outreachTitle;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        otherTenant = newTenant();
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "relfilesadmin");
        member = signInAs(tenant, OrganizationMemberRole.MEMBER, "relfilesmember");
        otherAdmin = signInAs(otherTenant, OrganizationMemberRole.ADMIN, "relfilesother");
        relationId = createRelation(tenant, admin, "Relation " + uniqueSuffix());
        String contactId = createContact(tenant, admin, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");
        outreachTitle = "Outreach " + uniqueSuffix();
        MvcResult created = createOutreachResult(tenant, admin, relationId, contactId, outreachTitle);
        mailId = read(created, "$.data.mail.id");
    }

    private String uploadToRelation(String content) throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE,
                ("[{\"fileName\":\"notes.pdf\",\"entityType\":\"RELATION\",\"entityId\":\"" + relationId + "\"}]")
                        .getBytes());
        MockMultipartFile file = new MockMultipartFile("files", "notes.pdf", MediaType.APPLICATION_PDF_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
        return read(mockMvc.perform(in(tenant, multipart(ATTACHMENTS_PATH).file(metadata).file(file), member.jwt(),
                null)).andExpect(status().isCreated()).andReturn(), "$.data[0].id");
    }

    private String snapshotOnMail() throws Exception {
        String legalEntityId = createLegalEntity(tenant, admin, "Korur " + uniqueSuffix().substring(0, 6));
        String documentId = read(mockMvc.perform(in(tenant, post(DOCUMENTS_PATH), member.jwt(),
                        "{\"entityType\":\"RELATION\",\"entityId\":\"" + relationId + "\",\"legalEntityId\":\""
                                + legalEntityId + "\",\"title\":\"Proposal\",\"type\":\"PROPOSAL\",\"language\":\"EN\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
        MockMultipartFile html = new MockMultipartFile("html", "a.html", "text/html", "<p>Hi</p>".getBytes());
        MockMultipartFile pdf = new MockMultipartFile("pdf", "a.pdf", "application/pdf",
                ("%PDF-1.4 " + uniqueSuffix()).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(in(tenant, multipart(HttpMethod.PUT, DOCUMENTS_PATH + "/" + documentId + "/files")
                .file(html).file(pdf), member.jwt(), null)).andExpect(status().isOk());
        mockMvc.perform(in(tenant, post(DOCUMENTS_PATH + "/" + documentId + "/attachments"), admin.jwt(),
                "{\"entityType\":\"MAIL\",\"entityId\":\"" + mailId + "\"}")).andExpect(status().isCreated());
        return documentId;
    }

    @Test
    void list_showsOwnAndMailFilesNewestFirstWithSourceDocumentAndOutreach() throws Exception {
        String ownId = uploadToRelation("%PDF own " + uniqueSuffix());
        String documentId = snapshotOnMail();

        mockMvc.perform(in(tenant, get(RELATIONS_PATH + "/" + relationId + "/attachments"), member.jwt(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].source").value("MAIL"))
                .andExpect(jsonPath("$.data[0].mailId").value(mailId))
                .andExpect(jsonPath("$.data[0].outreachTitle").value(outreachTitle))
                .andExpect(jsonPath("$.data[0].documentId").value(documentId))
                .andExpect(jsonPath("$.data[1].id").value(ownId))
                .andExpect(jsonPath("$.data[1].source").value("RELATION"))
                .andExpect(jsonPath("$.data[1].mailId").value(nullValue()))
                .andExpect(jsonPath("$.data[1].documentId").value(nullValue()));
    }

    @Test
    void relationOfAnotherOrganization_answers404OtkNot001() throws Exception {
        String foreignRelationId = createRelation(otherTenant, otherAdmin, "Foreign " + uniqueSuffix());

        mockMvc.perform(in(tenant, get(RELATIONS_PATH + "/" + foreignRelationId + "/attachments"), member.jwt(), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("OTK_NOT_001"));
    }
}
