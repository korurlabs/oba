package com.korur.oba.apps.otak.contact.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.util.Arrays;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.apps.otak.contact.enums.ContactEmailType;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that the
 * email type of a contact is stored and returned, that a type without an address is
 * refused, and that the database enum lists the values of {@link ContactEmailType} in
 * their declaration order.
 */
class ContactEmailTypeIT extends AbstractTenantIT {

    private static final String EMAIL_TYPE_WITHOUT_EMAIL_CODE = "OTK_VAL_005";
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

    @Test
    void createContact_emailWithType_returnsItAndListsIt() throws Exception {
        String body = "{\"name\":\"Jan de Vries\",\"kind\":\"PERSON\",\"decisionMaker\":false,\"mainPersonContact\":false,\"mainBusinessContact\":false,\"email\":\"info@fabrikam.example\",\"emailType\":\"SHARED\"}";

        MvcResult created = mockMvc.perform(in(tenant, post(contactsPath), user.jwt(), body))
                .andExpect(status().isCreated()).andReturn();
        MvcResult listed = mockMvc.perform(in(tenant, get(contactsPath), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(created, "$.data.emailType")).isEqualTo("SHARED");
        assertThat(read(listed, "$.data[0].emailType")).isEqualTo("SHARED");
    }

    @Test
    void createContact_typeWithoutEmail_answers422AndStoresNothing() throws Exception {
        String body = "{\"name\":\"Jan de Vries\",\"kind\":\"PERSON\",\"decisionMaker\":false,\"mainPersonContact\":false,\"mainBusinessContact\":false,\"emailType\":\"PERSONAL\"}";

        MvcResult result = mockMvc.perform(in(tenant, post(contactsPath), user.jwt(), body))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(EMAIL_TYPE_WITHOUT_EMAIL_CODE);
        assertThat(read(mockMvc.perform(in(tenant, get(contactsPath), user.jwt(), null))
                .andExpect(status().isOk()).andReturn(), "$.data.length()")).isEqualTo("0");
    }

    @Test
    void contactEmailTypeEnum_databaseOrder_matchesJavaDeclarationOrder() {
        List<String> databaseOrder = jdbcTemplate.queryForList(
                "SELECT unnest(enum_range(NULL::otak_contact_email_type_enum))::text", String.class);

        List<String> javaOrder = Arrays.stream(ContactEmailType.values()).map(Enum::name).toList();

        assertThat(databaseOrder).isEqualTo(javaOrder);
    }
}
