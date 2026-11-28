package com.korur.oba.apps.otak.outreach.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.support.AbstractOtakDocumentsIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, against the migrated PostgreSQL schema, the search, channel and mail sender filters
 * of the outreach list: the search matches the title, the contact's name and mail address and
 * the relation's name, case ignored and trimmed, a blank search lists everything, and every
 * filter combines with AND with the others and stays within the organization of the header.
 */
class OutreachListFilterIT extends AbstractOtakDocumentsIT {

    private static final String INSERT_MAIL_SENDER = "INSERT INTO otak.mail_senders "
            + "(mail_address, added_by, organization_id, type) VALUES (?, ?, ?, 'PERSONAL') RETURNING id";

    private Tenant tenant;
    private Person admin;
    private String fabrikamId;
    private String northwindId;
    private String mailSenderId;
    private String sentMailId;
    private String whatsappId;
    private String draftMailId;

    @BeforeEach
    void setUpOutreachesOfTwoRelations() throws Exception {
        tenant = newTenant();
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "otakadmin");

        fabrikamId = createRelation(tenant, admin, "Fabrikam Holding");
        String adaId = createNamedContact(fabrikamId, "Adaline Peters", "adaline@contoso.example");
        sentMailId = createMailOutreach(fabrikamId, adaId, "Spring offer");

        northwindId = createRelation(tenant, admin, "Northwind Traders");
        String phoneContactId = createContactWithPhone(tenant, admin, northwindId, "+31 6 1234 5678");
        whatsappId = read(mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        "{\"relationId\":\"" + northwindId + "\",\"contactId\":\"" + phoneContactId
                                + "\",\"type\":\"WHATSAPP\",\"title\":\"Quick hello\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
        String pietId = createNamedContact(northwindId, "Piet Bakker", "piet@northwind.example");
        draftMailId = createMailOutreach(northwindId, pietId, "Winter offer");

        mailSenderId = jdbcTemplate.queryForObject(INSERT_MAIL_SENDER, UUID.class,
                "sales" + uniqueSuffix() + "@" + tenant.domain(), admin.user().getId(),
                tenant.organization().getId()).toString();
        markSent(sentMailId);
        jdbcTemplate.update("UPDATE otak.outreaches SET mail_sender_id = ?::uuid WHERE id = ?::uuid",
                mailSenderId, sentMailId);
    }

    private String createNamedContact(String relationId, String name, String email) throws Exception {
        return read(mockMvc.perform(in(tenant, post(RELATIONS_PATH + "/" + relationId + "/contacts"), admin.jwt(),
                        "{\"name\":\"" + name + "\",\"kind\":\"PERSON\",\"decisionMaker\":false,"
                                + "\"mainPersonContact\":false,\"mainBusinessContact\":false,\"email\":\"" + email
                                + "\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private String createMailOutreach(String relationId, String contactId, String title) throws Exception {
        return read(createOutreachResult(tenant, admin, relationId, contactId, title), "$.data.id");
    }

    /** Lists the outreaches of the tenant with the given name and value pairs as query parameters. */
    private String list(String... parameters) throws Exception {
        MockHttpServletRequestBuilder request = get(OUTREACHES_PATH);
        for (int i = 0; i + 1 < parameters.length; i += 2) {
            request = request.param(parameters[i], parameters[i + 1]);
        }
        MvcResult result = mockMvc.perform(in(tenant, request, admin.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void searchOnRelationName_caseIgnored_returnsOnlyItsOutreach() throws Exception {
        assertThat(list("search", "FABRIKAM"))
                .contains(sentMailId).doesNotContain(whatsappId).doesNotContain(draftMailId);
    }

    @Test
    void searchOnContactName_trimmed_returnsOnlyItsOutreach() throws Exception {
        assertThat(list("search", "  adaline pet  "))
                .contains(sentMailId).doesNotContain(whatsappId).doesNotContain(draftMailId);
    }

    @Test
    void searchOnContactMailAddress_returnsOnlyItsOutreach() throws Exception {
        assertThat(list("search", "contoso.example"))
                .contains(sentMailId).doesNotContain(whatsappId).doesNotContain(draftMailId);
    }

    @Test
    void searchOnTitle_returnsEveryMatchingOutreach() throws Exception {
        assertThat(list("search", "Offer"))
                .contains(sentMailId).contains(draftMailId).doesNotContain(whatsappId);
    }

    @Test
    void searchWithoutMatch_returnsNothing() throws Exception {
        assertThat(list("search", "tailspin"))
                .doesNotContain(sentMailId).doesNotContain(whatsappId).doesNotContain(draftMailId);
    }

    @Test
    void blankSearch_isIgnoredAndReturnsEveryOutreach() throws Exception {
        assertThat(list("search", "   "))
                .contains(sentMailId).contains(whatsappId).contains(draftMailId);
    }

    @Test
    void searchLongerThan200Characters_answers422() throws Exception {
        mockMvc.perform(in(tenant, get(OUTREACHES_PATH).param("search", "a".repeat(201)), admin.jwt(), null))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void channelWhatsapp_returnsOnlyTheWhatsappOutreach() throws Exception {
        assertThat(list("channel", "WHATSAPP"))
                .contains(whatsappId).doesNotContain(sentMailId).doesNotContain(draftMailId);
    }

    @Test
    void channelMail_returnsOnlyTheMailOutreaches() throws Exception {
        assertThat(list("channel", "MAIL"))
                .contains(sentMailId).contains(draftMailId).doesNotContain(whatsappId);
    }

    @Test
    void unknownChannel_answers422() throws Exception {
        mockMvc.perform(in(tenant, get(OUTREACHES_PATH).param("channel", "EMAIL"), admin.jwt(), null))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void mailSender_returnsOnlyTheOutreachSentFromIt() throws Exception {
        assertThat(list("mailSenderId", mailSenderId))
                .contains(sentMailId).doesNotContain(whatsappId).doesNotContain(draftMailId);
    }

    @Test
    void unknownMailSender_returnsNothing() throws Exception {
        assertThat(list("mailSenderId", UUID.randomUUID().toString()))
                .doesNotContain(sentMailId).doesNotContain(whatsappId).doesNotContain(draftMailId);
    }

    @Test
    void searchChannelAndRelation_combineWithAnd() throws Exception {
        assertThat(list("search", "offer", "channel", "MAIL", "relationId", northwindId))
                .contains(draftMailId).doesNotContain(sentMailId).doesNotContain(whatsappId);
    }

    @Test
    void searchAndStatus_combineWithAnd() throws Exception {
        assertThat(list("search", "offer", "status", "SENT"))
                .contains(sentMailId).doesNotContain(draftMailId).doesNotContain(whatsappId);
    }

    @Test
    void mailSenderAndChannel_combineWithAnd() throws Exception {
        assertThat(list("mailSenderId", mailSenderId, "channel", "WHATSAPP"))
                .doesNotContain(sentMailId).doesNotContain(whatsappId).doesNotContain(draftMailId);
    }

    @Test
    void searchAndSenderWithHeaderOfAnotherOrganization_showNothingOfThisOne() throws Exception {
        Tenant other = newTenant();
        Person outsider = signInAs(other, OrganizationMemberRole.ADMIN, "otakoutsider");

        MvcResult result = mockMvc.perform(in(other, get(OUTREACHES_PATH).param("search", "fabrikam")
                        .param("mailSenderId", mailSenderId), outsider.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(sentMailId).doesNotContain("Spring offer");
    }
}
