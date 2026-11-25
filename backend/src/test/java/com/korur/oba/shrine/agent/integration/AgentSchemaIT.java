package com.korur.oba.shrine.agent.integration;

// 1. Framework-level
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.agent.routine.entity.AgentRoutine;
import com.korur.oba.shrine.agent.routine.repository.AgentRoutineRepository;
import com.korur.oba.shrine.agent.support.AbstractAgentIT;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.UserKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application starts with {@code ddl-auto=validate}, so reaching any test of this class proves that the
 * User columns of V1_2_135 and the agent routine entity of V1_2_136 match the migrated schema. The tests
 * add the loading of both mappings, the agent name query and the constraints of the schema.
 */
class AgentSchemaIT extends AbstractAgentIT {

    @Autowired
    private AgentRoutineRepository agentRoutineRepository;

    private static ConstraintViolationException violationOf(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation;
            }
            cause = cause.getCause();
        }
        throw new AssertionError("No ConstraintViolationException in the cause chain of " + failure);
    }

    @Test
    void agentUser_savedAndReloaded_keepsKindAndAgentDetail() {
        Tenant tenant = newTenant();

        User saved = insertAgent(tenant, "Ada " + uniqueSuffix());

        User loaded = userRepository.findById(saved.getId()).orElseThrow();
        assertEquals(UserKind.AGENT, loaded.getKind());
        assertEquals(AgentFlow.API, loaded.getAgentDetail().getFlow());
        assertEquals(AgentProvider.OPENAI, loaded.getAgentDetail().getProvider());
        assertEquals(tenant.organization().getId(), loaded.getAgentDetail().getOrganizationId());
    }

    @Test
    void person_loads_asHumanWithNullAgentDetail() {
        Tenant tenant = newTenant();

        User person = userRepository.findById(tenant.member().getUser().getId()).orElseThrow();

        assertEquals(UserKind.HUMAN, person.getKind());
        assertNull(person.getAgentDetail());
    }

    @Test
    void agentRow_withoutProvider_isRefusedByTheKindCheck() {
        Tenant tenant = newTenant();
        String suffix = uniqueSuffix();

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        INSERT INTO shrine.users (id, email, status, kind, agent_role, agent_flow, agent_organization_id)
                        VALUES (?, ?, 'ACTIVE'::shrine_identity_user_status_enum, 'AGENT'::shrine_identity_user_kind_enum,
                                'SALES_ENGINEER'::shrine_identity_agent_role_enum,
                                'API'::shrine_identity_agent_flow_enum, ?)
                        """, UUID.randomUUID(), "x" + suffix + "@agents.oba.invalid", tenant.organization().getId()));

        assertTrue(failure.getMessage().contains("users_kind_ck"), failure.getMessage());
    }

    @Test
    void humanRow_withAgentColumns_isRefusedByTheKindCheck() {
        String suffix = uniqueSuffix();

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        INSERT INTO shrine.users (id, email, status, kind, agent_role)
                        VALUES (?, ?, 'ACTIVE'::shrine_identity_user_status_enum, 'HUMAN'::shrine_identity_user_kind_enum,
                                'SALES_ENGINEER'::shrine_identity_agent_role_enum)
                        """, UUID.randomUUID(), "h" + suffix + "@example.com"));

        assertTrue(failure.getMessage().contains("users_kind_ck"), failure.getMessage());
    }

    @Test
    void secondLiveAgentNameIgnoringCase_violatesTheNamedUniqueIndex() {
        Tenant tenant = newTenant();
        String name = "Hal " + uniqueSuffix();
        insertAgent(tenant, name);

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> insertAgent(tenant, name.toUpperCase()));

        assertEquals(AgentConstant.AGENT_NAME_UNIQUE_INDEX, violationOf(failure).getConstraintName());
    }

    @Test
    void sameAgentNameInAnotherOrganization_isAllowed() {
        String name = "Hal " + uniqueSuffix();
        insertAgent(newTenant(), name);

        User other = insertAgent(newTenant(), name);

        assertNotNull(other.getId());
    }

    @Test
    void agentNameOfSoftDeletedAgent_isFreeAgain() {
        Tenant tenant = newTenant();
        String name = "Hal " + uniqueSuffix();
        User first = insertAgent(tenant, name);
        jdbcTemplate.update("UPDATE shrine.users SET deleted = TRUE WHERE id = ?", first.getId());

        User second = insertAgent(tenant, name);

        assertNotNull(second.getId());
    }

    @Test
    void existsAgentName_matchesIgnoringCaseWithinTheOrganizationAndHonoursTheExclusion() {
        Tenant tenant = newTenant();
        String name = "Eve " + uniqueSuffix();
        User agent = insertAgent(tenant, name);
        UUID organizationId = tenant.organization().getId();

        assertTrue(userRepository.existsAgentName(organizationId, name.toUpperCase(), null));
        assertFalse(userRepository.existsAgentName(organizationId, name, agent.getId()));
        assertFalse(userRepository.existsAgentName(newTenant().organization().getId(), name, null));
        assertFalse(userRepository.existsAgentName(organizationId, "Nobody " + uniqueSuffix(), null));
    }

    @Test
    void existsAgentName_ignoresPeopleWithTheSameFirstName() {
        Tenant tenant = newTenant();
        User person = tenant.member().getUser();
        person.getProfile().setFirstName("Pat " + uniqueSuffix());
        userRepository.saveAndFlush(person);

        assertFalse(userRepository.existsAgentName(
                tenant.organization().getId(), person.getProfile().getFirstName(), null));
    }

    @Test
    void agentRoutine_savedAndReloaded_matchesTheTable() {
        Tenant tenant = newTenant();
        User agent = insertAgent(tenant, "Rob " + uniqueSuffix());

        AgentRoutine saved = agentRoutineRepository.saveAndFlush(AgentRoutine.builder()
                .organizationId(tenant.organization().getId())
                .userId(agent.getId())
                .routineId(ROUTINE_ID)
                .routineSecretEncrypted("ciphertext")
                .routineSecretPrefix("sk-ant")
                .lastFireStatus(RoutineFireStatus.FIRED)
                .lastHttpStatus(200)
                .build());

        AgentRoutine loaded = agentRoutineRepository
                .findByOrganizationIdAndUserId(tenant.organization().getId(), agent.getId()).orElseThrow();
        assertEquals(saved.getId(), loaded.getId());
        assertEquals(RoutineFireStatus.FIRED, loaded.getLastFireStatus());
        assertEquals(ROUTINE_ID, loaded.getRoutineId());
    }
}
