package com.korur.oba.apps.otak.mailsender.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, through the real security chain, that a MEMBER reads mail senders but cannot add
 * or delete one, and that an ADMIN can delete one and the request validation answers 422.
 * The sender is inserted directly and no request reaches Microsoft Graph: a refused or
 * conflicting request stops before the mailbox lookup.
 */
class MailSenderAccessIT extends AbstractTenantIT {

    private static final String MAIL_SENDERS_PATH = "/otak/mail-senders";
    private static final String INSERT_MAIL_SENDER = "INSERT INTO otak.mail_senders "
            + "(mail_address, added_by, organization_id, type) VALUES (?, ?, ?, 'PERSONAL') RETURNING id";

    private Tenant tenant;
    private Person member;
    private Person admin;
    private String mailAddress;
    private String mailSenderId;

    @BeforeEach
    void setUpTenantAndMailSender() throws Exception {
        tenant = newTenant();
        member = signIn(tenant, "otakmember");
        // signIn(tenant, ...) already adds a MEMBER row, so the admin signs in without one and joins as ADMIN.
        admin = signIn("kc-" + uniqueSuffix(), "otakadmin" + uniqueSuffix().substring(0, 6) + "@" + tenant.domain());
        createMember(tenant.organization(), admin.user(), OrganizationMemberRole.ADMIN, OrganizationMemberStatus.ACTIVE);

        mailAddress = "sender" + uniqueSuffix() + "@" + tenant.domain();
        mailSenderId = jdbcTemplate.queryForObject(INSERT_MAIL_SENDER, UUID.class, mailAddress,
                admin.user().getId(), tenant.organization().getId()).toString();
    }

    private static String body(String mailAddress) {
        return "{\"type\":\"PERSONAL\",\"mailAddress\":\"" + mailAddress + "\"}";
    }

    private long sendersOfTenant() {
        return count("SELECT count(*) FROM otak.mail_senders WHERE organization_id = ?", tenant.organization().getId());
    }

    @Test
    void member_list_answers200WithTheSender() throws Exception {
        MvcResult list = mockMvc.perform(in(tenant, get(MAIL_SENDERS_PATH), member.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(list.getResponse().getContentAsString()).contains(mailSenderId).contains(mailAddress);
    }

    @Test
    void member_list_carriesTheTypeOfEachSender() throws Exception {
        String sharedId = jdbcTemplate.queryForObject("INSERT INTO otak.mail_senders "
                        + "(mail_address, added_by, organization_id, type) VALUES (?, ?, ?, 'SHARED') RETURNING id",
                UUID.class, "shared" + uniqueSuffix() + "@" + tenant.domain(), admin.user().getId(),
                tenant.organization().getId()).toString();

        mockMvc.perform(in(tenant, get(MAIL_SENDERS_PATH), member.jwt(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + mailSenderId + "')].type", contains("PERSONAL")))
                .andExpect(jsonPath("$.data[?(@.id == '" + sharedId + "')].type", contains("SHARED")));
    }

    @Test
    void member_post_answers403AndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post(MAIL_SENDERS_PATH), member.jwt(),
                        body("other" + uniqueSuffix() + "@" + tenant.domain())))
                .andExpect(status().isForbidden());

        assertThat(sendersOfTenant()).isEqualTo(1L);
    }

    @Test
    void member_delete_answers403AndDeletesNothing() throws Exception {
        mockMvc.perform(in(tenant, delete(MAIL_SENDERS_PATH + "/" + mailSenderId), member.jwt(), null))
                .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM otak.mail_senders WHERE id = ?::uuid", mailSenderId)).isEqualTo(1L);
    }

    @Test
    void admin_delete_answers200AndDeletes() throws Exception {
        mockMvc.perform(in(tenant, delete(MAIL_SENDERS_PATH + "/" + mailSenderId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM otak.mail_senders WHERE id = ?::uuid", mailSenderId)).isZero();
    }

    @Test
    void admin_postWithMalformedMailAddress_answers422AndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post(MAIL_SENDERS_PATH), admin.jwt(), body("not-a-mail-address")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(sendersOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_postWithTakenAddressDifferingInCase_answers409() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(MAIL_SENDERS_PATH), admin.jwt(),
                        body(mailAddress.toUpperCase())))
                .andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_STA_008");
        assertThat(sendersOfTenant()).isEqualTo(1L);
    }
}
