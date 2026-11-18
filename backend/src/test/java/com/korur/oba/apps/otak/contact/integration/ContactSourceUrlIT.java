package com.korur.oba.apps.otak.contact.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that the
 * source URL of a contact is validated on create and comes back in the response.
 */
class ContactSourceUrlIT extends AbstractTenantIT {

    private static final String VALIDATION_CODE = "GEN_VAL_001";
    private static final String RELATIONS_PATH = "/otak/relations";

    private Tenant tenant;
    private Person user;
    private String contactsPath;

    @BeforeEach
    void setUpRelation() throws Exception {
        tenant = newTenant();
        user = signIn(tenant, "otak");
        String relationId = read(mockMvc.perform(in(tenant, post(RELATIONS_PATH), user.jwt(),
                        "{\"name\":\"Relation " + uniqueSuffix() + "\",\"type\":\"LEAD\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
        contactsPath = RELATIONS_PATH + "/" + relationId + "/contacts";
    }

    private static String body(String sourceUrl) {
        return "{\"name\":\"Jan de Vries\",\"kind\":\"PERSON\",\"decisionMaker\":false,\"mainPersonContact\":false,\"mainBusinessContact\":false,\"sourceUrl\":\"" + sourceUrl + "\"}";
    }

    @Test
    void createContact_validSourceUrl_returnsItAndListsIt() throws Exception {
        String sourceUrl = "https://www.fabrikam.example/team";

        MvcResult created = mockMvc.perform(in(tenant, post(contactsPath), user.jwt(), body(sourceUrl)))
                .andExpect(status().isCreated()).andReturn();
        MvcResult listed = mockMvc.perform(in(tenant, get(contactsPath), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(created, "$.data.sourceUrl")).isEqualTo(sourceUrl);
        assertThat(read(listed, "$.data[0].sourceUrl")).isEqualTo(sourceUrl);
    }

    @Test
    void createContact_ftpSourceUrl_answers422AndStoresNothing() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(contactsPath), user.jwt(), body("ftp://x")))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(VALIDATION_CODE);
        assertThat(read(mockMvc.perform(in(tenant, get(contactsPath), user.jwt(), null))
                .andExpect(status().isOk()).andReturn(), "$.data.length()")).isEqualTo("0");
    }

    @Test
    void createContact_sourceUrlOf501Characters_answers422() throws Exception {
        String prefix = "https://fabrikam.example/";
        String url = prefix + "a".repeat(501 - prefix.length());

        MvcResult result = mockMvc.perform(in(tenant, post(contactsPath), user.jwt(), body(url)))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(VALIDATION_CODE);
    }
}
