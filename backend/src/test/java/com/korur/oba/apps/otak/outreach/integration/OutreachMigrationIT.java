package com.korur.oba.apps.otak.outreach.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

/**
 * The template, mail and outreach schema refuses rows the application must never write: a
 * duplicate template name, a mail on another relation, an outreach whose mail does not fit its
 * channel, a sent outreach without a time or an address; and its delete rules remove or keep the
 * right rows.
 */
class OutreachMigrationIT extends AbstractTenantIT {

    private Tenant tenant;
    private UUID organizationId;

    @BeforeEach
    void setUpTenant() {
        tenant = newTenant();
        organizationId = tenant.organization().getId();
    }

    private UUID relation(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.relations (id, name, type, stage, organization_id) "
                + "VALUES (?, ?, 'CUSTOMER'::otak_relation_type_enum, 'DIRECT'::otak_pipeline_stage_enum, ?)", id, "Rel " + uniqueSuffix(), organization);
        return id;
    }

    private UUID contact(UUID organization, UUID relation) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.contacts (id, name, organization_id, relation_id) VALUES (?, ?, ?, ?)",
                id, "Contact " + uniqueSuffix(), organization, relation);
        return id;
    }

    private UUID mailSender(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.mail_senders (id, mail_address, added_by, type, organization_id) "
                + "VALUES (?, ?, ?, 'PERSONAL'::otak_email_type_enum, ?)",
                id, "s" + uniqueSuffix() + "@example.com", UUID.randomUUID(), organization);
        return id;
    }

    private UUID mail(UUID organization, UUID relation) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.mails (id, subject, body, language, organization_id, relation_id) "
                + "VALUES (?, 'Subject', 'Body', 'NL'::otak_document_language_enum, ?, ?)",
                id, organization, relation);
        return id;
    }

    private UUID outreach(UUID organization, UUID relation, UUID contact, UUID mail, UUID sender) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.outreaches (id, title, type, organization_id, relation_id, contact_id, mail_id, mail_sender_id) "
                + "VALUES (?, 'Title', 'MAIL'::otak_outreach_type_enum, ?, ?, ?, ?, ?)",
                id, organization, relation, contact, mail, sender);
        return id;
    }

    private long countOf(String table, UUID organization) {
        return count("SELECT count(*) FROM otak." + table + " WHERE organization_id = ?", organization);
    }

    private void insertDocumentTemplate(String name, String type, String language, String html) {
        jdbcTemplate.update("INSERT INTO otak.document_templates (name, type, language, html_blob_url, organization_id) "
                + "VALUES (?, ?::otak_document_type_enum, ?::otak_document_language_enum, ?, ?)",
                name, type, language, html, organizationId);
    }

    private void insertMailTemplate(String name, String language, String subject, String body) {
        jdbcTemplate.update("INSERT INTO otak.mail_templates (name, language, subject, body, organization_id) "
                + "VALUES (?, ?::otak_document_language_enum, ?, ?, ?)",
                name, language, subject, body, organizationId);
    }

    @Test
    void insertDocumentTemplate_mailType_isRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insertDocumentTemplate("T " + uniqueSuffix(), "MAIL", "NL", null));
    }

    @Test
    void insertDocumentTemplate_ndaWithoutFiles_isAcceptedAsNotReady() {
        insertDocumentTemplate("T " + uniqueSuffix(), "NDA", "EN", null);

        assertEquals(1L, countOf("document_templates", organizationId));
    }

    @Test
    void insertDocumentTemplate_sameNameDifferentCaseSameLanguage_isRejected() {
        String name = "Nda " + uniqueSuffix();
        insertDocumentTemplate(name, "NDA", "NL", null);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertDocumentTemplate(name.toUpperCase(), "NDA", "NL", null));
    }

    @Test
    void insertMailTemplate_withoutSubject_isRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insertMailTemplate("T " + uniqueSuffix(), "NL", null, "Body"));
    }

    @Test
    void insertMailTemplate_withoutBody_isRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insertMailTemplate("T " + uniqueSuffix(), "NL", "Subject", null));
    }

    @Test
    void insertMailTemplate_bodyOver10000Characters_isRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insertMailTemplate("T " + uniqueSuffix(), "NL", "Subject", "x".repeat(10001)));
    }

    @Test
    void insertMailTemplate_sameNameDifferentCaseSameLanguage_isRejected() {
        String name = "Intro " + uniqueSuffix();
        insertMailTemplate(name, "NL", "Subject", "Body");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertMailTemplate(name.toUpperCase(), "NL", "Subject", "Body"));
    }

    @Test
    void insertMailTemplate_sameNameOtherLanguage_isAccepted() {
        String name = "Intro " + uniqueSuffix();
        insertMailTemplate(name, "NL", "Subject", "Body");
        insertMailTemplate(name, "EN", "Subject", "Body");

        assertEquals(2L, countOf("mail_templates", organizationId));
    }

    @Test
    void insertMail_relationOfAnotherOrganization_isRejectedByTheCompositeKey() {
        Tenant other = newTenant();
        UUID foreignRelation = relation(other.organization().getId());

        assertThrows(DataIntegrityViolationException.class, () -> mail(organizationId, foreignRelation));
    }

    @Test
    void insertMail_bodyOver10000Characters_isRejected() {
        UUID relation = relation(organizationId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO otak.mails (subject, body, language, organization_id, relation_id) "
                        + "VALUES ('S', ?, 'NL'::otak_document_language_enum, ?, ?)",
                "x".repeat(10001), organizationId, relation));
    }

    @Test
    void insertOutreach_mailOfAnotherRelation_isRejected() {
        UUID relation = relation(organizationId);
        UUID otherRelation = relation(organizationId);
        UUID mail = mail(organizationId, otherRelation);

        assertThrows(DataIntegrityViolationException.class,
                () -> outreach(organizationId, relation, null, mail, null));
    }

    @Test
    void insertOutreach_sameMailTwice_isRejected() {
        UUID relation = relation(organizationId);
        UUID mail = mail(organizationId, relation);
        outreach(organizationId, relation, null, mail, null);

        assertThrows(DataIntegrityViolationException.class,
                () -> outreach(organizationId, relation, null, mail, null));
    }

    @Test
    void insertOutreach_defaultsToDraft() {
        UUID relation = relation(organizationId);
        outreach(organizationId, relation, null, mail(organizationId, relation), null);

        assertEquals(1L, count("SELECT count(*) FROM otak.outreaches WHERE relation_id = ? AND status = 'DRAFT'", relation));
    }

    @Test
    void insertOutreach_callWithoutMail_isAccepted() {
        UUID relation = relation(organizationId);

        jdbcTemplate.update("INSERT INTO otak.outreaches (title, type, organization_id, relation_id) "
                + "VALUES ('Call', 'CALL'::otak_outreach_type_enum, ?, ?)", organizationId, relation);

        assertEquals(1L, count("SELECT count(*) FROM otak.outreaches WHERE relation_id = ? AND mail_id IS NULL", relation));
    }

    @Test
    void updateOutreach_sentWithoutSentAtAndAddress_isRejected() {
        UUID relation = relation(organizationId);
        UUID outreach = outreach(organizationId, relation, null, mail(organizationId, relation), null);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE otak.outreaches SET status = 'SENT'::otak_outreach_status_enum WHERE id = ?", outreach));
    }

    @Test
    void updateOutreach_sentWithSentAtAndAddress_isAccepted() {
        UUID relation = relation(organizationId);
        UUID outreach = outreach(organizationId, relation, null, mail(organizationId, relation), null);

        jdbcTemplate.update("UPDATE otak.outreaches SET status = 'SENT'::otak_outreach_status_enum, "
                + "sent_at = CURRENT_TIMESTAMP, sent_to = 'a@example.com' WHERE id = ?", outreach);

        assertEquals(1L, count("SELECT count(*) FROM otak.outreaches WHERE id = ? AND status = 'SENT'", outreach));
    }

    @Test
    void deleteRelation_removesMailsAndOutreaches() {
        UUID relation = relation(organizationId);
        UUID mail = mail(organizationId, relation);
        outreach(organizationId, relation, null, mail, null);

        jdbcTemplate.update("DELETE FROM otak.relations WHERE id = ?", relation);

        assertEquals(0L, count("SELECT count(*) FROM otak.mails WHERE relation_id = ?", relation));
        assertEquals(0L, count("SELECT count(*) FROM otak.outreaches WHERE relation_id = ?", relation));
    }

    @Test
    void deleteMail_usedByAnOutreach_isRejected() {
        UUID relation = relation(organizationId);
        UUID mail = mail(organizationId, relation);
        outreach(organizationId, relation, null, mail, null);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM otak.mails WHERE id = ?", mail));
    }

    @Test
    void deleteMail_afterItsOutreach_isAccepted() {
        UUID relation = relation(organizationId);
        UUID mail = mail(organizationId, relation);
        UUID outreach = outreach(organizationId, relation, null, mail, null);

        jdbcTemplate.update("DELETE FROM otak.outreaches WHERE id = ?", outreach);
        jdbcTemplate.update("DELETE FROM otak.mails WHERE id = ?", mail);

        assertEquals(0L, count("SELECT count(*) FROM otak.mails WHERE id = ?", mail));
    }

    @Test
    void deleteContactAndMailSender_keepTheOutreachAndClearTheLinks() {
        UUID relation = relation(organizationId);
        UUID contact = contact(organizationId, relation);
        UUID sender = mailSender(organizationId);
        UUID outreach = outreach(organizationId, relation, contact, mail(organizationId, relation), sender);

        jdbcTemplate.update("DELETE FROM otak.contacts WHERE id = ?", contact);
        jdbcTemplate.update("DELETE FROM otak.mail_senders WHERE id = ?", sender);

        assertEquals(1L, count("SELECT count(*) FROM otak.outreaches WHERE id = ? "
                + "AND contact_id IS NULL AND mail_sender_id IS NULL", outreach));
    }

    @Test
    void deleteMailTemplate_keepsMailsCopiedFromIt() {
        UUID relation = relation(organizationId);
        UUID template = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.mail_templates (id, name, language, subject, body, organization_id) "
                + "VALUES (?, 'Intro', 'NL'::otak_document_language_enum, 'S', 'B', ?)", template, organizationId);
        UUID mail = mail(organizationId, relation);
        jdbcTemplate.update("UPDATE otak.mails SET mail_template_id = ? WHERE id = ?", template, mail);

        jdbcTemplate.update("DELETE FROM otak.mail_templates WHERE id = ?", template);

        assertEquals(1L, count("SELECT count(*) FROM otak.mails WHERE id = ? AND mail_template_id IS NULL", mail));
    }

    @Test
    void deleteOrganization_removesItsTemplatesAndRelationsContentAndKeepsThoseOfOthers() {
        Organization doomed = createOrganization(OrganizationStatus.ACTIVE);
        UUID doomedId = doomed.getId();
        UUID relation = relation(doomedId);
        outreach(doomedId, relation, null, mail(doomedId, relation), null);
        jdbcTemplate.update("INSERT INTO otak.mail_templates (name, language, subject, body, organization_id) "
                + "VALUES ('Doomed', 'NL'::otak_document_language_enum, 'S', 'B', ?)", doomedId);
        jdbcTemplate.update("INSERT INTO otak.document_templates (name, type, language, organization_id) "
                + "VALUES ('Doomed', 'NDA'::otak_document_type_enum, 'NL'::otak_document_language_enum, ?)", doomedId);
        insertMailTemplate("Kept " + uniqueSuffix(), "NL", "Subject", "Body");

        jdbcTemplate.update("DELETE FROM shrine.organizations WHERE id = ?", doomedId);

        assertEquals(0L, countOf("mail_templates", doomedId));
        assertEquals(0L, countOf("document_templates", doomedId));
        assertEquals(0L, countOf("mails", doomedId));
        assertEquals(0L, countOf("outreaches", doomedId));
        assertEquals(1L, countOf("mail_templates", organizationId));
    }
}
