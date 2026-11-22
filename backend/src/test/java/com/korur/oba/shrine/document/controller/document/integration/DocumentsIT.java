package com.korur.oba.shrine.document.controller.document.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.nio.charset.StandardCharsets;

// 4. Internal
import com.korur.oba.apps.otak.support.AbstractOtakDocumentsIT;
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, through the real security chain, the database and the blob store, the life of a
 * document of a relation: create from a template copies its files, updates and file uploads
 * record the last editor and remove the old blobs, a snapshot on a mail is a copy of its own
 * that later uploads never change, the snapshot rules each answer their code, a deleted
 * document leaves its snapshots, a relation delete removes its documents, Otak access decides
 * who may read, and a legal entity in use can not be deleted.
 */
class DocumentsIT extends AbstractOtakDocumentsIT {

    private static final String DOCUMENTS_PATH = "/documents";

    @MockitoSpyBean
    private AzureBlobStorageService azureBlobStorageService;

    private Tenant tenant;
    private Tenant otherTenant;
    private Person admin;
    private Person member;
    private Person otherAdmin;
    private String legalEntityId;
    private String relationId;
    private String outreachId;
    private String mailId;
    private String outreachTitle;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        otherTenant = newTenant();
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "docadmin");
        member = signInAs(tenant, OrganizationMemberRole.MEMBER, "docmember");
        otherAdmin = signInAs(otherTenant, OrganizationMemberRole.ADMIN, "docother");
        legalEntityId = createLegalEntity(tenant, admin, "Korur " + uniqueSuffix().substring(0, 6));
        relationId = createRelation(tenant, admin, "Relation " + uniqueSuffix());
        String contactId = createContact(tenant, admin, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");
        outreachTitle = "Outreach " + uniqueSuffix();
        MvcResult created = createOutreachResult(tenant, admin, relationId, contactId, outreachTitle);
        outreachId = read(created, "$.data.id");
        mailId = read(created, "$.data.mail.id");
    }

    // ---- helpers ----

    private static String createBody(String relationId, String legalEntityId, String templateId) {
        return "{\"entityType\":\"RELATION\",\"entityId\":\"" + relationId + "\",\"legalEntityId\":\""
                + legalEntityId + "\",\"title\":\"Proposal Fabrikam\",\"type\":\"PROPOSAL\",\"language\":\"EN\""
                + (templateId == null ? "" : ",\"templateId\":\"" + templateId + "\"") + "}";
    }

    private static String attachBody(String entityType, String entityId) {
        return "{\"entityType\":\"" + entityType + "\",\"entityId\":\"" + entityId + "\"}";
    }

    private ResultActions createAs(Person person, String templateId) throws Exception {
        return mockMvc.perform(in(tenant, post(DOCUMENTS_PATH), person.jwt(),
                createBody(relationId, legalEntityId, templateId)));
    }

    private String createDocument(Person person) throws Exception {
        return read(createAs(person, null).andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private ResultActions uploadFiles(Person person, String path, String pdfContent) throws Exception {
        MockMultipartFile html = new MockMultipartFile("html", "a.html", "text/html", "<p>Hi</p>".getBytes());
        MockMultipartFile pdf = new MockMultipartFile("pdf", "a.pdf", "application/pdf",
                ("%PDF-1.4 " + pdfContent).getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(in(tenant, multipart(HttpMethod.PUT, path + "/files").file(html).file(pdf),
                person.jwt(), null));
    }

    private String readyDocument(Person person) throws Exception {
        String documentId = createDocument(person);
        uploadFiles(person, DOCUMENTS_PATH + "/" + documentId, "first " + uniqueSuffix())
                .andExpect(status().isOk());
        return documentId;
    }

    private ResultActions attach(Person person, String documentId, String entityType, String entityId)
            throws Exception {
        return mockMvc.perform(in(tenant, post(DOCUMENTS_PATH + "/" + documentId + "/attachments"), person.jwt(),
                attachBody(entityType, entityId)));
    }

    private String documentColumn(String documentId, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + "::text FROM shrine.documents WHERE id = ?::uuid",
                String.class, documentId);
    }

    private String attachmentColumn(String attachmentId, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + "::text FROM shrine.attachments WHERE id = ?::uuid",
                String.class, attachmentId);
    }

    private void revokeOtakFromEveryone() {
        jdbcTemplate.update("UPDATE shrine.organization_app_grants SET deleted = TRUE "
                + "WHERE organization_id = ? AND app = 'OTAK'::shrine_app_grant_app_enum", tenant.organization().getId());
    }

    // ---- create, update, files ----

    @Test
    void createFromReadyTemplate_copiesItsFilesToNewBlobsAndRecordsTheCreatorAsLastEditor() throws Exception {
        String templateId = createFileTemplate(tenant, admin, "Template " + uniqueSuffix());
        uploadFiles(admin, TEMPLATES_PATH + "/" + templateId, "template " + uniqueSuffix()).andExpect(status().isOk());

        MvcResult result = createAs(member, templateId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.templateId").value(templateId))
                .andExpect(jsonPath("$.data.relationId").value(relationId))
                .andExpect(jsonPath("$.data.legalEntityId").value(legalEntityId))
                .andExpect(jsonPath("$.data.updatedBy.id").value(member.userId()))
                .andExpect(jsonPath("$.data.snapshotCount").value(0))
                .andReturn();
        String documentId = read(result, "$.data.id");

        String templateHtml = jdbcTemplate.queryForObject(
                "SELECT html_blob_url FROM shrine.document_templates WHERE id = ?::uuid", String.class, templateId);
        assertThat(documentColumn(documentId, "html_blob_url")).isNotNull().isNotEqualTo(templateHtml);
        assertThat(documentColumn(documentId, "created_by_id")).isEqualTo(member.userId());
    }

    @Test
    void createWithLegalEntityOfAnotherOrganization_answers404LgeNot001AndStoresNothing() throws Exception {
        String foreignLegalEntityId = createLegalEntity(otherTenant, otherAdmin, "Other " + uniqueSuffix().substring(0, 6));

        mockMvc.perform(in(tenant, post(DOCUMENTS_PATH), member.jwt(),
                        createBody(relationId, foreignLegalEntityId, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LGE_NOT_001"));
        assertThat(count("SELECT count(*) FROM shrine.documents WHERE entity_id = ?::uuid", relationId)).isZero();
    }

    @Test
    void update_replacesFieldsAndRecordsTheLastEditor() throws Exception {
        String documentId = createDocument(admin);

        mockMvc.perform(in(tenant, put(DOCUMENTS_PATH + "/" + documentId), member.jwt(),
                        "{\"title\":\"NDA Fabrikam\",\"type\":\"NDA\",\"language\":\"NL\",\"legalEntityId\":\""
                                + legalEntityId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("NDA Fabrikam"))
                .andExpect(jsonPath("$.data.updatedBy.id").value(member.userId()));
        assertThat(documentColumn(documentId, "updated_by_id")).isEqualTo(member.userId());
    }

    @Test
    void replaceFiles_recordsTheLastEditorAndRemovesTheOldBlobs() throws Exception {
        String documentId = readyDocument(admin);
        String oldHtml = documentColumn(documentId, "html_blob_url");
        String oldPdf = documentColumn(documentId, "pdf_blob_url");

        uploadFiles(member, DOCUMENTS_PATH + "/" + documentId, "second " + uniqueSuffix())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedBy.id").value(member.userId()));

        assertThat(documentColumn(documentId, "updated_by_id")).isEqualTo(member.userId());
        assertThat(documentColumn(documentId, "pdf_blob_url")).isNotEqualTo(oldPdf);
        verify(azureBlobStorageService).deleteFile(oldHtml);
        verify(azureBlobStorageService).deleteFile(oldPdf);
    }

    // ---- snapshots ----

    @Test
    void attach_copiesThePdfToANewBlobThatALaterUploadLeavesUnchanged() throws Exception {
        String documentId = readyDocument(member);

        MvcResult result = attach(admin, documentId, "MAIL", mailId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.documentId").value(documentId))
                .andExpect(jsonPath("$.data.entityId").value(mailId))
                .andExpect(jsonPath("$.data.fileName").value("Proposal Fabrikam.pdf"))
                .andReturn();
        String attachmentId = read(result, "$.data.id");
        String snapshotUrl = attachmentColumn(attachmentId, "url");
        String snapshotHash = attachmentColumn(attachmentId, "file_hash");
        assertThat(snapshotUrl).isNotEqualTo(documentColumn(documentId, "pdf_blob_url"));

        uploadFiles(member, DOCUMENTS_PATH + "/" + documentId, "changed " + uniqueSuffix())
                .andExpect(status().isOk());

        assertThat(attachmentColumn(attachmentId, "url")).isEqualTo(snapshotUrl);
        assertThat(attachmentColumn(attachmentId, "file_hash")).isEqualTo(snapshotHash);
        mockMvc.perform(in(tenant, get(DOCUMENTS_PATH + "/" + documentId), member.jwt(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotCount").value(1))
                .andExpect(jsonPath("$.data.snapshots[0].attachmentId").value(attachmentId))
                .andExpect(jsonPath("$.data.snapshots[0].mailId").value(mailId))
                .andExpect(jsonPath("$.data.snapshots[0].outreachTitle").value(outreachTitle));
    }

    @Test
    void attach_documentWithoutPdfAnswers422DocVal003() throws Exception {
        String documentId = createDocument(admin);

        attach(admin, documentId, "MAIL", mailId).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DOC_VAL_003"));
    }

    @Test
    void attach_mailOfAnotherRelationAnswers422DocVal004() throws Exception {
        String documentId = readyDocument(admin);
        String otherRelationId = createRelation(tenant, admin, "Other " + uniqueSuffix());
        String otherContactId = createContact(tenant, admin, otherRelationId, "piet" + uniqueSuffix() + "@contoso.example");
        String otherMailId = read(createOutreachResult(tenant, admin, otherRelationId, otherContactId,
                "Other outreach " + uniqueSuffix()), "$.data.mail.id");

        attach(admin, documentId, "MAIL", otherMailId).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DOC_VAL_004"));
        assertThat(count("SELECT count(*) FROM shrine.attachments WHERE entity_id = ?::uuid", otherMailId)).isZero();
    }

    @Test
    void attach_sentMailAnswers409OtkSta011() throws Exception {
        String documentId = readyDocument(admin);
        markSent(outreachId);

        attach(admin, documentId, "MAIL", mailId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OTK_STA_011"));
        assertThat(count("SELECT count(*) FROM shrine.attachments WHERE entity_id = ?::uuid", mailId)).isZero();
    }

    @Test
    void attach_memberIsRefusedBecauseMailWritesNeedAnOtakAdmin() throws Exception {
        String documentId = readyDocument(member);

        attach(member, documentId, "MAIL", mailId).andExpect(status().isForbidden());
    }

    // ---- delete ----

    @Test
    void delete_documentWithSnapshot_keepsTheSnapshotFileWithDocumentIdNull() throws Exception {
        String documentId = readyDocument(admin);
        String attachmentId = read(attach(admin, documentId, "MAIL", mailId).andExpect(status().isCreated())
                .andReturn(), "$.data.id");
        String snapshotUrl = attachmentColumn(attachmentId, "url");

        mockMvc.perform(in(tenant, delete(DOCUMENTS_PATH + "/" + documentId), member.jwt(), null))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM shrine.documents WHERE id = ?::uuid", documentId)).isZero();
        assertThat(attachmentColumn(attachmentId, "url")).isEqualTo(snapshotUrl);
        assertThat(attachmentColumn(attachmentId, "document_id")).isNull();
    }

    @Test
    void relationDelete_removesItsDocumentsAndTheirBlobs() throws Exception {
        String documentId = readyDocument(admin);
        String html = documentColumn(documentId, "html_blob_url");
        String pdf = documentColumn(documentId, "pdf_blob_url");

        mockMvc.perform(in(tenant, delete(RELATIONS_PATH + "/" + relationId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM shrine.documents WHERE id = ?::uuid", documentId)).isZero();
        verify(azureBlobStorageService).deleteFile(html);
        verify(azureBlobStorageService).deleteFile(pdf);
    }

    // ---- access ----

    @Test
    void memberWithoutOtakAccess_cannotListOrReadTheRelationsDocuments() throws Exception {
        String documentId = createDocument(admin);
        revokeOtakFromEveryone();

        mockMvc.perform(in(tenant, get(DOCUMENTS_PATH).param("relationId", relationId), member.jwt(), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("APP_SEC_001"));
        mockMvc.perform(in(tenant, get(DOCUMENTS_PATH), member.jwt(), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("APP_SEC_001"));
        mockMvc.perform(in(tenant, get(DOCUMENTS_PATH + "/" + documentId), member.jwt(), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("APP_SEC_001"));
    }

    @Test
    void list_filtersByRelationAndShowsRelationAndLegalEntityNames() throws Exception {
        String documentId = createDocument(admin);
        String otherRelationId = createRelation(tenant, admin, "Other " + uniqueSuffix());
        mockMvc.perform(in(tenant, post(DOCUMENTS_PATH), admin.jwt(),
                createBody(otherRelationId, legalEntityId, null))).andExpect(status().isCreated());

        mockMvc.perform(in(tenant, get(DOCUMENTS_PATH).param("relationId", relationId), member.jwt(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(documentId))
                .andExpect(jsonPath("$.data[0].relationName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].legalEntityShortName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].updatedBy.displayName").isNotEmpty());
    }

    @Test
    void documentOfAnotherOrganization_answers404DocNot002() throws Exception {
        String documentId = createDocument(admin);

        mockMvc.perform(in(otherTenant, get(DOCUMENTS_PATH + "/" + documentId), otherAdmin.jwt(), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DOC_NOT_002"));
    }

    // ---- legal entity in use ----

    @Test
    void legalEntityDelete_namedByADocument_answers409LgeSta002AndKeepsIt() throws Exception {
        createDocument(admin);

        mockMvc.perform(in(tenant, delete(LEGAL_ENTITIES_PATH + "/" + legalEntityId), admin.jwt(), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LGE_STA_002"));
        assertThat(count("SELECT count(*) FROM shrine.legal_entities WHERE id = ?::uuid", legalEntityId))
                .isEqualTo(1L);
    }

    @Test
    void legalEntityDelete_namedByATemplate_answers409LgeSta002() throws Exception {
        String templateLegalEntityId = createLegalEntity(tenant, admin, "Tpl " + uniqueSuffix().substring(0, 6));
        mockMvc.perform(in(tenant, post(TEMPLATES_PATH), admin.jwt(),
                fileTemplateBody("Template " + uniqueSuffix(), templateLegalEntityId))).andExpect(status().isCreated());

        mockMvc.perform(in(tenant, delete(LEGAL_ENTITIES_PATH + "/" + templateLegalEntityId), admin.jwt(), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LGE_STA_002"));
    }

    @Test
    void legalEntityDelete_unused_answers200() throws Exception {
        String unusedId = createLegalEntity(tenant, admin, "Free " + uniqueSuffix().substring(0, 6));

        mockMvc.perform(in(tenant, delete(LEGAL_ENTITIES_PATH + "/" + unusedId), admin.jwt(), null))
                .andExpect(status().isOk());
    }
}
