package com.korur.oba.apps.otak.outreach.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.support.AbstractOtakDocumentsIT;
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, through the real security chain and the blob store, that the files of a mail and of a relation go
 * through /storage/attachments under their own rules: a mail is written by an ADMIN and locked once its outreach
 * is SENT, a relation is written by a MEMBER and refused to a GUEST, another organization's mail or relation is
 * refused before anything is stored, another organization's attachment is not found on delete, the same file
 * twice on one parent is refused, and a blob that two rows share survives the delete of one of them.
 */
class OtakAttachmentAccessIT extends AbstractOtakDocumentsIT {

    private static final String ATTACHMENTS_PATH = "/storage/attachments";
    private static final String ORGANIZATION_HEADER = "X-Organization-ID";
    private static final String ORGANIZATION_MISMATCH = "APP_SEC_002";
    private static final String FILE_NOT_FOUND = "STG_NOT_001";
    private static final String ROLE_TOO_LOW = "ORG_SEC_003";
    private static final String MAIL_SENT = "OTK_STA_011";

    @MockitoSpyBean
    private AzureBlobStorageService azureBlobStorageService;

    private Tenant tenant;
    private Tenant otherTenant;
    private Person admin;
    private Person member;
    private Person guest;
    private Person otherAdmin;
    private String relationId;
    private String contactId;
    private String outreachId;
    private String mailId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        otherTenant = newTenant();
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "otakadmin");
        member = signInAs(tenant, OrganizationMemberRole.MEMBER, "otakmember");
        guest = signInAs(tenant, OrganizationMemberRole.GUEST, "otakguest");
        otherAdmin = signInAs(otherTenant, OrganizationMemberRole.ADMIN, "otakother");
        relationId = createRelation(tenant, admin, "Relation " + uniqueSuffix());
        contactId = createContact(tenant, admin, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");
        MvcResult created = createOutreachResult(tenant, admin, relationId, contactId, "Outreach " + uniqueSuffix());
        outreachId = read(created, "$.data.id");
        mailId = read(created, "$.data.mail.id");
    }

    private static MockMultipartFile json(String body) {
        return new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, body.getBytes());
    }

    private static MockMultipartFile pdf(String content) {
        return new MockMultipartFile("files", "offer.pdf", MediaType.APPLICATION_PDF_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
    }

    private static String metadata(String entityType, String entityId) {
        return "[{\"fileName\":\"offer.pdf\",\"entityType\":\"" + entityType + "\",\"entityId\":\"" + entityId + "\"}]";
    }

    private ResultActions upload(Tenant header, Person person, String entityType,
                                                                      String entityId, String content)
            throws Exception {
        return mockMvc.perform(multipart(ATTACHMENTS_PATH).file(json(metadata(entityType, entityId)))
                .file(pdf(content)).with(person.jwt()).header(ORGANIZATION_HEADER, header.id()));
    }

    private ResultActions list(Tenant header, Person person, String entityType,
                                                                    String entityId) throws Exception {
        return mockMvc.perform(get(ATTACHMENTS_PATH).param("entityType", entityType).param("entityId", entityId)
                .with(person.jwt()).header(ORGANIZATION_HEADER, header.id()));
    }

    private ResultActions remove(Tenant header, Person person, String id)
            throws Exception {
        return mockMvc.perform(delete(ATTACHMENTS_PATH + "/" + id).with(person.jwt())
                .header(ORGANIZATION_HEADER, header.id()));
    }

    private long attachmentsOf(String entityId) {
        return count("SELECT count(*) FROM shrine.attachments WHERE entity_id = ?::uuid", entityId);
    }

    private String uploadedId(MvcResult result) throws Exception {
        return read(result, "$.data[0].id");
    }

    private String uploadAs(Person person, String entityType, String entityId, String content) throws Exception {
        return uploadedId(upload(tenant, person, entityType, entityId, content)
                .andExpect(status().isCreated()).andReturn());
    }

    private String readyTemplate(String name, String pdfContent) throws Exception {
        String templateId = createFileTemplate(tenant, admin, name);
        MockMultipartFile html = new MockMultipartFile("html", "a.html", "text/html", "<p>Hi</p>".getBytes());
        MockMultipartFile pdf = new MockMultipartFile("pdf", "a.pdf", "application/pdf",
                ("%PDF-1.4 " + pdfContent).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(in(tenant, multipart(HttpMethod.PUT, TEMPLATES_PATH + "/" + templateId + "/files")
                .file(html).file(pdf), admin.jwt(), null)).andExpect(status().isOk());
        return templateId;
    }

    private static String copyBody(String entityType, String entityId) {
        return "{\"entityType\":\"" + entityType + "\",\"entityId\":\"" + entityId + "\"}";
    }

    // ---- MAIL ----

    @Test
    void mail_adminUploadsListsAndDeletes() throws Exception {
        String first = uploadAs(admin, "MAIL", mailId, "%PDF first " + uniqueSuffix());
        String second = uploadAs(admin, "MAIL", mailId, "%PDF second " + uniqueSuffix());

        list(tenant, admin, "MAIL", mailId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].id").value(org.hamcrest.Matchers.contains(first, second)));

        remove(tenant, admin, first).andExpect(status().isOk());

        assertThat(attachmentsOf(mailId)).isEqualTo(1L);
        verify(azureBlobStorageService).deleteFile(anyString());
    }

    @Test
    void mail_memberReadsButWriteAndDeleteAnswer403RoleTooLow() throws Exception {
        String attachmentId = uploadAs(admin, "MAIL", mailId, "%PDF kept " + uniqueSuffix());
        clearInvocations(azureBlobStorageService);

        list(tenant, member, "MAIL", mailId).andExpect(status().isOk());
        MvcResult refusedUpload = upload(tenant, member, "MAIL", mailId, "%PDF member " + uniqueSuffix())
                .andExpect(status().isForbidden()).andReturn();
        MvcResult refusedDelete = remove(tenant, member, attachmentId)
                .andExpect(status().isForbidden()).andReturn();

        assertThat(refusedUpload.getResponse().getContentAsString()).contains(ROLE_TOO_LOW);
        assertThat(refusedDelete.getResponse().getContentAsString()).contains(ROLE_TOO_LOW);
        assertThat(attachmentsOf(mailId)).isEqualTo(1L);
        verify(azureBlobStorageService, never()).uploadFile(anyString(), any(byte[].class));
        verify(azureBlobStorageService, never()).deleteFile(anyString());
    }

    @Test
    void mail_guestListAnswers403() throws Exception {
        list(tenant, guest, "MAIL", mailId).andExpect(status().isForbidden());
    }

    @Test
    void mail_unknownMailAnswers404OtkNot011() throws Exception {
        MvcResult result = upload(tenant, admin, "MAIL", UUID.randomUUID().toString(), "%PDF none")
                .andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_NOT_011");
    }

    @Test
    void mail_sameFileTwiceOnOneMailAnswers422AndOnTwoMailsIsAllowed() throws Exception {
        String content = "%PDF same " + uniqueSuffix();
        uploadAs(admin, "MAIL", mailId, content);
        String otherMailId = read(createOutreachResult(tenant, admin, relationId, contactId, "Second " + uniqueSuffix()),
                "$.data.mail.id");

        MvcResult duplicate = upload(tenant, admin, "MAIL", mailId, content)
                .andExpect(status().isUnprocessableEntity()).andReturn();
        upload(tenant, admin, "MAIL", otherMailId, content).andExpect(status().isCreated());

        assertThat(duplicate.getResponse().getContentAsString()).contains("STG_VAL_005");
        assertThat(attachmentsOf(mailId)).isEqualTo(1L);
        assertThat(attachmentsOf(otherMailId)).isEqualTo(1L);
    }

    @Test
    void mail_afterSend_uploadAndDeleteAnswer409OtkSta011AndChangeNothing() throws Exception {
        String attachmentId = uploadAs(admin, "MAIL", mailId, "%PDF locked " + uniqueSuffix());
        markSent(outreachId);
        clearInvocations(azureBlobStorageService);

        MvcResult refusedUpload = upload(tenant, admin, "MAIL", mailId, "%PDF late " + uniqueSuffix())
                .andExpect(status().isConflict()).andReturn();
        MvcResult refusedDelete = remove(tenant, admin, attachmentId).andExpect(status().isConflict()).andReturn();
        list(tenant, member, "MAIL", mailId).andExpect(status().isOk());

        assertThat(refusedUpload.getResponse().getContentAsString()).contains(MAIL_SENT);
        assertThat(refusedDelete.getResponse().getContentAsString()).contains(MAIL_SENT);
        assertThat(attachmentsOf(mailId)).isEqualTo(1L);
        verify(azureBlobStorageService, never()).uploadFile(anyString(), any(byte[].class));
        verify(azureBlobStorageService, never()).deleteFile(anyString());
    }

    @Test
    void mail_failedOutreachStaysEditable() throws Exception {
        jdbcTemplate.update("UPDATE otak.outreaches SET status = CAST('FAILED' AS otak_outreach_status_enum) "
                + "WHERE id = ?::uuid", outreachId);

        upload(tenant, admin, "MAIL", mailId, "%PDF retry " + uniqueSuffix()).andExpect(status().isCreated());
    }

    @Test
    void mail_ofAnotherOrganization_listAndUploadAnswer403_deleteAnswers404_andNothingChanges() throws Exception {
        String otherRelationId = createRelation(otherTenant, otherAdmin, "Other " + uniqueSuffix());
        String otherContactId = createContact(otherTenant, otherAdmin, otherRelationId,
                "x" + uniqueSuffix() + "@fabrikam.example");
        String otherMailId = read(createOutreachResult(otherTenant, otherAdmin, otherRelationId, otherContactId,
                "Other " + uniqueSuffix()), "$.data.mail.id");
        String otherAttachmentId = uploadedId(upload(otherTenant, otherAdmin, "MAIL", otherMailId,
                "%PDF other " + uniqueSuffix()).andExpect(status().isCreated()).andReturn());
        clearInvocations(azureBlobStorageService);

        MvcResult listed = list(tenant, admin, "MAIL", otherMailId).andExpect(status().isForbidden()).andReturn();
        MvcResult uploaded = upload(tenant, admin, "MAIL", otherMailId, "%PDF cross " + uniqueSuffix())
                .andExpect(status().isForbidden()).andReturn();
        // The attachment is looked up within the request organization, so another organization's one is not
        // found and its existence is not revealed.
        MvcResult deleted = remove(tenant, admin, otherAttachmentId).andExpect(status().isNotFound()).andReturn();

        assertThat(listed.getResponse().getContentAsString()).contains(ORGANIZATION_MISMATCH);
        assertThat(uploaded.getResponse().getContentAsString()).contains(ORGANIZATION_MISMATCH);
        assertThat(deleted.getResponse().getContentAsString()).contains(FILE_NOT_FOUND);
        assertThat(attachmentsOf(otherMailId)).isEqualTo(1L);
        verify(azureBlobStorageService, never()).uploadFile(anyString(), any(byte[].class));
        verify(azureBlobStorageService, never()).deleteFile(anyString());
    }

    // ---- RELATION ----

    @Test
    void relation_memberUploadsListsAndDeletes() throws Exception {
        String attachmentId = uploadAs(member, "RELATION", relationId, "%PDF rfp " + uniqueSuffix());

        list(tenant, member, "RELATION", relationId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        remove(tenant, member, attachmentId).andExpect(status().isOk());

        assertThat(attachmentsOf(relationId)).isZero();
    }

    @Test
    void relation_adminWritesToo() throws Exception {
        uploadAs(admin, "RELATION", relationId, "%PDF admin " + uniqueSuffix());

        assertThat(attachmentsOf(relationId)).isEqualTo(1L);
    }

    @Test
    void relation_guestReadAndWriteAnswer403() throws Exception {
        list(tenant, guest, "RELATION", relationId).andExpect(status().isForbidden());
        upload(tenant, guest, "RELATION", relationId, "%PDF guest " + uniqueSuffix())
                .andExpect(status().isForbidden());

        assertThat(attachmentsOf(relationId)).isZero();
    }

    @Test
    void relation_unknownRelationAnswers404OtkNot001() throws Exception {
        MvcResult result = upload(tenant, member, "RELATION", UUID.randomUUID().toString(), "%PDF none")
                .andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_NOT_001");
    }

    @Test
    void relation_ofAnotherOrganizationAnswers403OrganizationMismatchAndStoresNothing() throws Exception {
        String otherRelationId = createRelation(otherTenant, otherAdmin, "Other " + uniqueSuffix());
        clearInvocations(azureBlobStorageService);

        MvcResult listed = list(tenant, member, "RELATION", otherRelationId)
                .andExpect(status().isForbidden()).andReturn();
        MvcResult uploaded = upload(tenant, member, "RELATION", otherRelationId, "%PDF cross " + uniqueSuffix())
                .andExpect(status().isForbidden()).andReturn();

        assertThat(listed.getResponse().getContentAsString()).contains(ORGANIZATION_MISMATCH);
        assertThat(uploaded.getResponse().getContentAsString()).contains(ORGANIZATION_MISMATCH);
        assertThat(attachmentsOf(otherRelationId)).isZero();
        verify(azureBlobStorageService, never()).uploadFile(anyString(), any(byte[].class));
    }

    @Test
    void relation_deleteRemovesTheRelationItsMailsAndTheirAttachments() throws Exception {
        uploadAs(member, "RELATION", relationId, "%PDF rel " + uniqueSuffix());
        uploadAs(admin, "MAIL", mailId, "%PDF mail " + uniqueSuffix());

        mockMvc.perform(in(tenant, delete(RELATIONS_PATH + "/" + relationId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(attachmentsOf(relationId)).isZero();
        assertThat(attachmentsOf(mailId)).isZero();
        assertThat(count("SELECT count(*) FROM otak.relations WHERE id = ?::uuid", relationId)).isZero();
    }

    // ---- Shared blob ----

    @Test
    void sharedBlob_deleteOfOneRowKeepsTheBlobAndDeleteOfTheLastRowRemovesIt() throws Exception {
        String relationAttachmentId = uploadAs(member, "RELATION", relationId, "%PDF shared " + uniqueSuffix());
        String url = jdbcTemplate.queryForObject("SELECT url FROM shrine.attachments WHERE id = ?::uuid",
                String.class, relationAttachmentId);
        UUID mailAttachmentId = jdbcTemplate.queryForObject("""
                INSERT INTO shrine.attachments (url, file_name, content_type, file_size, entity_type, entity_id,
                                                organization_id)
                VALUES (?, 'offer.pdf', 'application/pdf', 10,
                        'MAIL'::shrine_storage_entity_type_enum, ?::uuid, ?)
                RETURNING id
                """, UUID.class, url, mailId, tenant.organization().getId());
        clearInvocations(azureBlobStorageService);

        remove(tenant, member, relationAttachmentId).andExpect(status().isOk());
        verify(azureBlobStorageService, never()).deleteFile(anyString());
        remove(tenant, admin, mailAttachmentId.toString()).andExpect(status().isOk());

        verify(azureBlobStorageService).deleteFile(url);
    }

    @Test
    void sharedBlob_bulkDeleteOfTheOutreachKeepsABlobThatARelationRowHolds() throws Exception {
        String relationAttachmentId = uploadAs(member, "RELATION", relationId, "%PDF shared " + uniqueSuffix());
        String url = jdbcTemplate.queryForObject("SELECT url FROM shrine.attachments WHERE id = ?::uuid",
                String.class, relationAttachmentId);
        jdbcTemplate.update("""
                INSERT INTO shrine.attachments (url, file_name, content_type, file_size, entity_type, entity_id,
                                                organization_id)
                VALUES (?, 'offer.pdf', 'application/pdf', 10,
                        'MAIL'::shrine_storage_entity_type_enum, ?::uuid, ?)
                """, url, mailId, tenant.organization().getId());
        clearInvocations(azureBlobStorageService);

        mockMvc.perform(in(tenant, delete(OUTREACHES_PATH + "/" + outreachId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(attachmentsOf(mailId)).isZero();
        assertThat(attachmentsOf(relationId)).isEqualTo(1L);
        verify(azureBlobStorageService, never()).deleteFile(url);
    }

    // ---- Template copy ----

    @Test
    void templateCopy_toMailAnswers201WithTemplateNamedPdfAndAppearsInTheList() throws Exception {
        String name = "Voorstel " + uniqueSuffix();
        String templateId = readyTemplate(name, "%PDF template " + uniqueSuffix());

        MvcResult result = mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + templateId + "/attachments"),
                        admin.jwt(), copyBody("MAIL", mailId))).andExpect(status().isCreated()).andReturn();

        assertThat(read(result, "$.data.fileName")).isEqualTo(name + ".pdf");
        list(tenant, member, "MAIL", mailId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileName").value(name + ".pdf"));
    }

    @Test
    void templateCopy_sameTemplateTwiceOnOneMailAnswers422Stg005() throws Exception {
        String templateId = readyTemplate("Twice " + uniqueSuffix(), "%PDF twice " + uniqueSuffix());
        mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + templateId + "/attachments"), admin.jwt(),
                copyBody("MAIL", mailId))).andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + templateId + "/attachments"),
                admin.jwt(), copyBody("MAIL", mailId))).andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("STG_VAL_005");
        assertThat(attachmentsOf(mailId)).isEqualTo(1L);
    }

    @Test
    void templateCopy_memberToMailAnswers403AndToRelationAnswers201() throws Exception {
        String templateId = readyTemplate("Member " + uniqueSuffix(), "%PDF member " + uniqueSuffix());

        mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + templateId + "/attachments"), member.jwt(),
                copyBody("MAIL", mailId))).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + templateId + "/attachments"), member.jwt(),
                copyBody("RELATION", relationId))).andExpect(status().isCreated());

        assertThat(attachmentsOf(mailId)).isZero();
        assertThat(attachmentsOf(relationId)).isEqualTo(1L);
    }

    @Test
    void templateCopy_toSentMailAnswers409AndStoresNothing() throws Exception {
        String templateId = readyTemplate("Late " + uniqueSuffix(), "%PDF late " + uniqueSuffix());
        markSent(outreachId);
        clearInvocations(azureBlobStorageService);

        MvcResult result = mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + templateId + "/attachments"),
                admin.jwt(), copyBody("MAIL", mailId))).andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(MAIL_SENT);
        assertThat(attachmentsOf(mailId)).isZero();
        verify(azureBlobStorageService, never()).uploadFile(anyString(), any(byte[].class));
    }

    @Test
    void templateCopy_toMailOfAnotherOrganizationAnswers403OrganizationMismatch() throws Exception {
        String templateId = readyTemplate("Cross " + uniqueSuffix(), "%PDF cross " + uniqueSuffix());
        String otherRelationId = createRelation(otherTenant, otherAdmin, "Other " + uniqueSuffix());
        String otherContactId = createContact(otherTenant, otherAdmin, otherRelationId,
                "x" + uniqueSuffix() + "@fabrikam.example");
        String otherMailId = read(createOutreachResult(otherTenant, otherAdmin, otherRelationId, otherContactId,
                "Other " + uniqueSuffix()), "$.data.mail.id");
        clearInvocations(azureBlobStorageService);

        MvcResult toMail = mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + templateId + "/attachments"),
                admin.jwt(), copyBody("MAIL", otherMailId))).andExpect(status().isForbidden()).andReturn();
        MvcResult toRelation = mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + templateId + "/attachments"),
                admin.jwt(), copyBody("RELATION", otherRelationId))).andExpect(status().isForbidden()).andReturn();

        assertThat(toMail.getResponse().getContentAsString()).contains(ORGANIZATION_MISMATCH);
        assertThat(toRelation.getResponse().getContentAsString()).contains(ORGANIZATION_MISMATCH);
        assertThat(attachmentsOf(otherMailId)).isZero();
        assertThat(attachmentsOf(otherRelationId)).isZero();
        verify(azureBlobStorageService, never()).uploadFile(anyString(), any(byte[].class));
    }

    @Test
    void templateCopy_templateOfAnotherOrganizationAnswers404OtkNot007() throws Exception {
        String otherTemplateId = createFileTemplate(otherTenant, otherAdmin, "Foreign " + uniqueSuffix());

        MvcResult result = mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + otherTemplateId + "/attachments"),
                admin.jwt(), copyBody("RELATION", relationId))).andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("DOC_NOT_001");
    }

    @Test
    void templateCopy_otherEntityTypeAnswers422() throws Exception {
        String templateId = readyTemplate("Ticket " + uniqueSuffix(), "%PDF ticket " + uniqueSuffix());

        mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + templateId + "/attachments"), admin.jwt(),
                copyBody("TICKET", UUID.randomUUID().toString()))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void templateCopy_afterTheTemplateIsDeleted_theCopyStaysIntact() throws Exception {
        String templateId = readyTemplate("Gone " + uniqueSuffix(), "%PDF gone " + uniqueSuffix());
        mockMvc.perform(in(tenant, post(TEMPLATES_PATH + "/" + templateId + "/attachments"), admin.jwt(),
                copyBody("RELATION", relationId))).andExpect(status().isCreated());
        clearInvocations(azureBlobStorageService);

        mockMvc.perform(in(tenant, delete(TEMPLATES_PATH + "/" + templateId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(attachmentsOf(relationId)).isEqualTo(1L);
        String copyUrl = jdbcTemplate.queryForObject(
                "SELECT url FROM shrine.attachments WHERE entity_id = ?::uuid", String.class, relationId);
        verify(azureBlobStorageService, never()).deleteFile(copyUrl);
    }
}
