package com.korur.oba.apps.otak.relation.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that an invalid relation
 * update or create is refused with 422 before it reaches the service and writes nothing: an update
 * without the required name, an update with an empty optional text, and a create without the required type.
 */
class RelationUpdateValidationIT extends AbstractTenantIT {

    private static final String VALIDATION_CODE = "GEN_VAL_001";
    private static final String RELATIONS_PATH = "/otak/relations";

    private Tenant tenant;
    private Person user;
    private String relationId;
    private String originalName;

    @BeforeEach
    void setUpRelation() throws Exception {
        tenant = newTenant();
        user = signIn(tenant, "otakval");
        originalName = "Relation " + uniqueSuffix();
        relationId = read(mockMvc.perform(in(tenant, post(RELATIONS_PATH), user.jwt(),
                        "{\"name\":\"" + originalName + "\",\"type\":\"LEAD\",\"notes\":\"Original notes\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    @Test
    void updateRelation_withoutName_answers422AndLeavesTheRowUnchanged() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, put(RELATIONS_PATH + "/" + relationId), user.jwt(),
                        "{\"notes\":\"Changed notes\"}"))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(VALIDATION_CODE).contains("name");
        assertThat(count("SELECT count(*) FROM otak.relations WHERE id = ?::uuid AND name = ? "
                + "AND notes = 'Original notes'", relationId, originalName)).isEqualTo(1L);
    }

    @Test
    void updateRelation_withEmptyNotes_answers422AndLeavesTheRowUnchanged() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, put(RELATIONS_PATH + "/" + relationId), user.jwt(),
                        "{\"name\":\"Renamed\",\"notes\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(VALIDATION_CODE).contains("notes");
        assertThat(count("SELECT count(*) FROM otak.relations WHERE id = ?::uuid AND name = ? "
                + "AND notes = 'Original notes'", relationId, originalName)).isEqualTo(1L);
    }

    @Test
    void createRelation_withoutType_answers422AndStoresNothing() throws Exception {
        String name = "Without type " + uniqueSuffix();

        MvcResult result = mockMvc.perform(in(tenant, post(RELATIONS_PATH), user.jwt(),
                        "{\"name\":\"" + name + "\"}"))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(VALIDATION_CODE).contains("type");
        assertThat(count("SELECT count(*) FROM otak.relations WHERE name = ?", name)).isZero();
    }
}
