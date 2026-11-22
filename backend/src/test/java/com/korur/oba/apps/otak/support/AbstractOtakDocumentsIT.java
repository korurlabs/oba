package com.korur.oba.apps.otak.support;

// 1. Framework-level
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 4. Internal
import com.korur.oba.shrine.document.support.AbstractDocumentTemplateIT;

/**
 * Shared set-up of the access and isolation ITs of mail templates and outreaches: the
 * requests that create a relation, a contact, a mail template and an outreach. Sign-in with
 * a chosen role and document templates come from {@link AbstractDocumentTemplateIT}. No file
 * is uploaded here, so document templates stay not ready and no helper here needs the blob
 * store.
 */
public abstract class AbstractOtakDocumentsIT extends AbstractDocumentTemplateIT {

    protected static final String MAIL_TEMPLATES_PATH = "/otak/mail-templates";
    protected static final String OUTREACHES_PATH = "/otak/outreaches";
    protected static final String RELATIONS_PATH = "/otak/relations";

    protected String createRelation(Tenant tenant, Person person, String name) throws Exception {
        return read(mockMvc.perform(in(tenant, post(RELATIONS_PATH), person.jwt(),
                        "{\"name\":\"" + name + "\",\"type\":\"LEAD\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    protected String createContact(Tenant tenant, Person person, String relationId, String email) throws Exception {
        return read(mockMvc.perform(in(tenant, post(RELATIONS_PATH + "/" + relationId + "/contacts"), person.jwt(),
                        "{\"name\":\"Jan de Vries\",\"kind\":\"PERSON\",\"decisionMaker\":false,\"mainPersonContact\":false,\"mainBusinessContact\":false,\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    /** A contact with a phone number as typed, and no email address. */
    protected String createContactWithPhone(Tenant tenant, Person person, String relationId, String phone)
            throws Exception {
        return read(mockMvc.perform(in(tenant, post(RELATIONS_PATH + "/" + relationId + "/contacts"), person.jwt(),
                        "{\"name\":\"Jan de Vries\",\"kind\":\"PERSON\",\"decisionMaker\":false,\"mainPersonContact\":false,\"mainBusinessContact\":false,\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    protected static String mailTemplateBody(String name) {
        return "{\"name\":\"" + name + "\",\"language\":\"NL\",\"subject\":\"Subject\",\"body\":\"Body\"}";
    }

    protected String createMailTemplate(Tenant tenant, Person person, String name) throws Exception {
        return read(mockMvc.perform(in(tenant, post(MAIL_TEMPLATES_PATH), person.jwt(), mailTemplateBody(name)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    protected static String outreachBody(String relationId, String contactId, String title) {
        return "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId + "\",\"type\":\"MAIL\",\"title\":\"" + title
                + "\",\"mail\":{\"language\":\"NL\",\"subject\":\"Subject\",\"body\":\"Body\"}}";
    }

    protected static String outreachUpdateBody(String contactId, String title) {
        return "{\"contactId\":\"" + contactId + "\",\"title\":\"" + title
                + "\",\"mail\":{\"language\":\"NL\",\"subject\":\"Subject\",\"body\":\"Body\"}}";
    }

    protected MvcResult createOutreachResult(Tenant tenant, Person person, String relationId, String contactId,
                                             String title) throws Exception {
        return mockMvc.perform(in(tenant, post(OUTREACHES_PATH), person.jwt(),
                        outreachBody(relationId, contactId, title)))
                .andExpect(status().isCreated()).andReturn();
    }

    /** Marks the outreach as sent in the database, the state a successful send leaves behind. */
    protected void markSent(String outreachId) {
        jdbcTemplate.update("UPDATE otak.outreaches SET status = CAST('SENT' AS otak_outreach_status_enum), "
                + "sent_at = now(), sent_to = 'jan@fabrikam.example' WHERE id = ?::uuid", outreachId);
    }
}
