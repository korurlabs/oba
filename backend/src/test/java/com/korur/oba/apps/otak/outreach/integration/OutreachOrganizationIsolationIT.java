package com.korur.oba.apps.otak.outreach.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.integration.AbstractOtakDocumentsIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that the
 * outreaches of an organization are closed to every other organization: a user who sits in
 * two organizations reaches an outreach only with the header of the organization that owns
 * it; every other header answers 404 without its data and changes nothing, and a relation or
 * contact of one organization can not be used to start an outreach in the other.
 */
class OutreachOrganizationIsolationIT extends AbstractOtakDocumentsIT {

    private static final String OUTREACH_NOT_FOUND_CODE = "OTK_NOT_005";
    private static final String RELATION_NOT_FOUND_CODE = "OTK_NOT_001";

    private Tenant tenantA;
    private Tenant tenantB;
    private Person user;
    private String relationId;
    private String contactId;
    private String title;
    private String outreachId;

    @BeforeEach
    void setUpTenantsAndOutreachOfA() throws Exception {
        tenantA = newTenant();
        tenantB = newTenant();
        user = signIn("kc-" + uniqueSuffix(), "otakoutreach" + uniqueSuffix().substring(0, 6) + "@" + tenantA.domain());
        // ADMIN in both, so even the ADMIN-only writes reach the lookup with the header of B.
        join(tenantA, user, OrganizationMemberRole.ADMIN);
        join(tenantB, user, OrganizationMemberRole.ADMIN);

        relationId = createRelation(tenantA, user, "Relation of A " + uniqueSuffix());
        contactId = createContact(tenantA, user, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");
        title = "Outreach of A " + uniqueSuffix();
        outreachId = read(createOutreachResult(tenantA, user, relationId, contactId, title), "$.data.id");
    }

    private void assertAnswersNotFound(MockHttpServletRequestBuilder request, String code) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains(code).doesNotContain(title);
    }

    @Test
    void getWithHeaderOfAnotherOrganization_answers404WithoutData() throws Exception {
        assertAnswersNotFound(in(tenantB, get(OUTREACHES_PATH + "/" + outreachId), user.jwt(), null),
                OUTREACH_NOT_FOUND_CODE);
    }

    @Test
    void putWithHeaderOfAnotherOrganization_answers404AndChangesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, put(OUTREACHES_PATH + "/" + outreachId), user.jwt(),
                outreachUpdateBody(contactId, "Hijacked")), OUTREACH_NOT_FOUND_CODE);

        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND title = ?",
                outreachId, title)).isEqualTo(1L);
    }

    @Test
    void sendWithHeaderOfAnotherOrganization_answers404AndClaimsNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, post(OUTREACHES_PATH + "/" + outreachId + "/send"), user.jwt(),
                "{\"mailSenderId\":\"" + java.util.UUID.randomUUID() + "\"}"), OUTREACH_NOT_FOUND_CODE);

        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND status::text = 'DRAFT'",
                outreachId)).isEqualTo(1L);
    }

    @Test
    void deleteWithHeaderOfAnotherOrganization_answers404AndDeletesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, delete(OUTREACHES_PATH + "/" + outreachId), user.jwt(), null),
                OUTREACH_NOT_FOUND_CODE);

        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid", outreachId)).isEqualTo(1L);
    }

    @Test
    void postWithRelationOfAnotherOrganization_answers404AndStoresNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, post(OUTREACHES_PATH), user.jwt(),
                outreachBody(relationId, contactId, "Cross")), RELATION_NOT_FOUND_CODE);

        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE organization_id = ?::uuid", tenantB.id()))
                .isZero();
    }

    @Test
    void listWithHeaderOfAnotherOrganization_showsNothingOfTheOutreachOfA() throws Exception {
        MvcResult other = mockMvc.perform(in(tenantB, get(OUTREACHES_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult own = mockMvc.perform(in(tenantA, get(OUTREACHES_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(other.getResponse().getContentAsString()).doesNotContain(outreachId).doesNotContain(title);
        assertThat(own.getResponse().getContentAsString()).contains(outreachId).contains(title);
    }

    @Test
    void getWithHeaderOfTheOwningOrganization_sameUser_answers200WithData() throws Exception {
        MvcResult result = mockMvc.perform(in(tenantA, get(OUTREACHES_PATH + "/" + outreachId), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(result, "$.data.title")).isEqualTo(title);
    }
}
