package com.korur.oba.shrine.document.support;

// 1. Framework-level
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

/**
 * Shared set-up of the document template ITs and of the app ITs that use templates: sign-in
 * with a chosen organization role, and the requests that create a legal entity and a
 * document template. No file is uploaded here, so templates stay not ready and no helper
 * here needs the blob store.
 */
public abstract class AbstractDocumentTemplateIT extends AbstractTenantIT {

    protected static final String TEMPLATES_PATH = "/document-templates";
    protected static final String LEGAL_ENTITIES_PATH = "/legal-entities";

    /** Signs in a user who joins the tenant with the given role and no other membership. */
    protected Person signInAs(Tenant tenant, OrganizationMemberRole role, String localPart) throws Exception {
        // signIn(tenant, ...) would add a MEMBER row; signing in without one lets the role be chosen.
        Person person = signIn("kc-" + uniqueSuffix(),
                localPart + uniqueSuffix().substring(0, 6) + "@" + tenant.domain());
        createMember(tenant.organization(), person.user(), role, OrganizationMemberStatus.ACTIVE);
        return person;
    }

    /** Adds an existing person to another tenant with the given role. */
    protected void join(Tenant tenant, Person person, OrganizationMemberRole role) {
        createMember(tenant.organization(), person.user(), role, OrganizationMemberStatus.ACTIVE);
    }

    /** An NDA template in English without a legal entity; it has no files, so it is not ready. */
    protected static String fileTemplateBody(String name) {
        return "{\"type\":\"NDA\",\"name\":\"" + name + "\",\"language\":\"EN\"}";
    }

    /** An NDA template in English issued by the given legal entity; it has no files. */
    protected static String fileTemplateBody(String name, String legalEntityId) {
        return "{\"type\":\"NDA\",\"name\":\"" + name + "\",\"language\":\"EN\",\"legalEntityId\":\""
                + legalEntityId + "\"}";
    }

    protected String createFileTemplate(Tenant tenant, Person person, String name) throws Exception {
        return read(mockMvc.perform(in(tenant, post(TEMPLATES_PATH), person.jwt(), fileTemplateBody(name)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    /** Creates a legal entity of the tenant; the person must be an admin or owner. */
    protected String createLegalEntity(Tenant tenant, Person person, String shortName) throws Exception {
        return read(mockMvc.perform(in(tenant, post(LEGAL_ENTITIES_PATH), person.jwt(),
                        "{\"shortName\":\"" + shortName + "\",\"legalName\":\"" + shortName
                                + " Holding\",\"country\":\"NL\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }
}
