package com.korur.oba.shrine.agent.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.AppGrantTargetType;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.enums.AgentRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentHelperUnitTest {

    @Mock
    private AppAccessQueryService appAccessQueryService;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private AuditService auditService;

    private AgentHelper agentHelper;

    @BeforeEach
    void setUp() {
        agentHelper = new AgentHelper(appAccessQueryService, auditLogMapper, auditService);
    }

    @Test
    void isNameConflict_nameIndexInCauseChain_returnsTrue() {
        assertTrue(AgentHelper.isNameConflict(integrityViolation(AgentConstant.AGENT_NAME_UNIQUE_INDEX)));
    }

    @Test
    void isNameConflict_otherConstraintOrNoConstraint_returnsFalse() {
        assertFalse(AgentHelper.isNameConflict(integrityViolation("users_email_uk")));
        assertFalse(AgentHelper.isNameConflict(new DataIntegrityViolationException("violation")));
    }

    @Test
    void userGrants_mixedGrants_keepsOnlyTheAgentsUserGrants() {
        User actor = User.builder().id(UUID.randomUUID()).build();
        UUID organizationId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID agentGrantId = UUID.randomUUID();
        when(appAccessQueryService.listGrants(actor, organizationId)).thenReturn(List.of(
                grant(agentGrantId, App.KAZAN, AppGrantTargetType.USER, agentId),
                grant(UUID.randomUUID(), App.KAYA, AppGrantTargetType.USER, UUID.randomUUID()),
                grant(UUID.randomUUID(), App.KALKAN, AppGrantTargetType.GROUP, null)));

        Map<UUID, App> grants = agentHelper.userGrants(actor, organizationId, agentId);

        assertEquals(Map.of(agentGrantId, App.KAZAN), grants);
    }

    @Test
    void describe_roleAndApps_listsAppNamesInNameOrder() {
        User agent = User.builder().id(UUID.randomUUID())
                .agentDetail(UserAgentDetail.builder().role(AgentRole.SALES_ENGINEER).build())
                .build();

        Map<String, Object> values = agentHelper.describe(agent, List.of(App.OTAK, App.KAZAN));

        assertEquals("SALES_ENGINEER", values.get(AgentConstant.FIELD_ROLE));
        assertEquals(List.of("KAZAN", "OTAK"), values.get(AgentConstant.FIELD_APPS));
    }

    private static AppGrantResponse grant(UUID id, App app, AppGrantTargetType targetType, UUID userId) {
        return new AppGrantResponse(id, app, targetType, userId, "Agent", null, null, null, Instant.now());
    }

    private static DataIntegrityViolationException integrityViolation(String constraintName) {
        return new DataIntegrityViolationException("violation",
                new ConstraintViolationException("violation", new SQLException("duplicate"), constraintName));
    }
}
