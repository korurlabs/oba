package com.korur.oba.apps.otak.relation.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema, that the hasContacts filter of the relations
 * list splits the leads of an organization into those with a contact and those without one,
 * and that leaving it out lists both.
 */
class RelationContactFilterIT extends AbstractTenantIT {

    private static final String RELATIONS_PATH = "/otak/relations";

    private Tenant tenant;
    private Person user;
    private String search;
    private String withContactId;
    private String withoutContactId;

    @BeforeEach
    void setUpLeadWithAndWithoutContact() throws Exception {
        tenant = newTenant();
        user = signIn(tenant, "otak");
        String suffix = uniqueSuffix();
        search = "&search=" + suffix;

        withContactId = createLead("With contact " + suffix);
        withoutContactId = createLead("Without contact " + suffix);
        mockMvc.perform(in(tenant, post(RELATIONS_PATH + "/" + withContactId + "/contacts"), user.jwt(),
                        "{\"name\":\"Anna de Vries\",\"kind\":\"PERSON\",\"decisionMaker\":false,\"mainPersonContact\":false,\"mainBusinessContact\":false,\"email\":\"anna@example.com\"}"))
                .andExpect(status().isCreated());
    }

    private String createLead(String name) throws Exception {
        return read(mockMvc.perform(in(tenant, post(RELATIONS_PATH), user.jwt(),
                        "{\"name\":\"" + name + "\",\"type\":\"LEAD\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private String list(String filter) throws Exception {
        return mockMvc.perform(in(tenant, get(RELATIONS_PATH + "?type=LEAD" + search + filter), user.jwt(), null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void listWithHasContactsFalse_returnsOnlyRelationsWithoutContact() throws Exception {
        assertThat(list("&hasContacts=false")).contains(withoutContactId).doesNotContain(withContactId);
    }

    @Test
    void listWithHasContactsTrue_returnsOnlyRelationsWithContact() throws Exception {
        assertThat(list("&hasContacts=true")).contains(withContactId).doesNotContain(withoutContactId);
    }

    @Test
    void listWithoutHasContacts_returnsBoth() throws Exception {
        assertThat(list("")).contains(withContactId).contains(withoutContactId);
    }
}
