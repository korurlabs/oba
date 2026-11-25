package com.korur.oba.shrine.agent.validator;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;

@ExtendWith(MockitoExtension.class)
class AgentValidatorUnitTest {

    @Mock
    private UserQueryService userQueryService;
    @Mock
    private LoggingService loggingService;

    private AgentValidator agentValidator;

    @BeforeEach
    void setUp() {
        agentValidator = new AgentValidator(userQueryService, loggingService);
    }

    @Test
    void ensureCallerIsNotAgent_agentCaller_throwsForbiddenAgentActionRefused() {
        User agent = user(UserKind.AGENT);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> agentValidator.ensureCallerIsNotAgent(agent, AgentConstant.ACTION_TICKET_DONE));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(AgentConstant.ACTION_TICKET_DONE, exception.getErrorValues().get(AgentConstant.FIELD_ACTION));
    }

    @Test
    void ensureCallerIsNotAgent_humanCaller_passes() {
        assertDoesNotThrow(() -> agentValidator.ensureCallerIsNotAgent(user(UserKind.HUMAN),
                AgentConstant.ACTION_TICKET_DONE));
    }

    @Test
    void ensureTargetIsNotAgent_agentTarget_throwsValidationAgentTargetRefused() {
        User agent = user(UserKind.AGENT);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> agentValidator.ensureTargetIsNotAgent(agent, AgentConstant.ACTION_MEMBER_ROLE));

        assertEquals("AGT_VAL_001", exception.getErrorCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        assertEquals(agent.getId().toString(), exception.getErrorValues().get(AgentConstant.FIELD_USER_ID));
    }

    @Test
    void ensureTargetIsNotAgent_humanOrMissingTarget_passes() {
        assertDoesNotThrow(() -> agentValidator.ensureTargetIsNotAgent(user(UserKind.HUMAN),
                AgentConstant.ACTION_MEMBER_ROLE));
        assertDoesNotThrow(() -> agentValidator.ensureTargetIsNotAgent(null, AgentConstant.ACTION_MEMBER_ROLE));
    }

    @Test
    void ensureNoAgentAmong_agentAmongUsers_throwsValidationAgentTargetRefused() {
        User person = user(UserKind.HUMAN);
        User agent = user(UserKind.AGENT);
        when(userQueryService.getUserEntitiesByIds(List.of(person.getId(), agent.getId())))
                .thenReturn(List.of(person, agent));

        ValidationException exception = assertThrows(ValidationException.class, () -> agentValidator
                .ensureNoAgentAmong(List.of(person.getId(), agent.getId(), person.getId()),
                        AgentConstant.ACTION_TICKET_REVIEWER));

        assertEquals("AGT_VAL_001", exception.getErrorCode());
        assertEquals(agent.getId().toString(), exception.getErrorValues().get(AgentConstant.FIELD_USER_ID));
    }

    @Test
    void ensureNoAgentAmong_onlyPeople_passes() {
        User person = user(UserKind.HUMAN);
        when(userQueryService.getUserEntitiesByIds(List.of(person.getId()))).thenReturn(List.of(person));

        assertDoesNotThrow(() -> agentValidator.ensureNoAgentAmong(List.of(person.getId()),
                AgentConstant.ACTION_TICKET_REVIEWER));
    }

    @Test
    void ensureNoAgentAmong_noUsers_queriesNothing() {
        agentValidator.ensureNoAgentAmong(List.of(), AgentConstant.ACTION_TICKET_REVIEWER);
        agentValidator.ensureNoAgentAmong(null, AgentConstant.ACTION_TICKET_REVIEWER);

        verify(userQueryService, never()).getUserEntitiesByIds(anyList());
    }

    @Test
    void ensureNameFree_nameTaken_throwsConflictNameTaken() {
        UUID organizationId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        when(userQueryService.existsAgentName(organizationId, "Pentest agent", agentId)).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> agentValidator.ensureNameFree(organizationId, "Pentest agent", agentId));

        assertEquals("AGT_STA_001", exception.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Pentest agent", exception.getErrorValues().get(AgentConstant.FIELD_NAME));
    }

    @Test
    void ensureNameFree_nameFree_passes() {
        UUID organizationId = UUID.randomUUID();
        when(userQueryService.existsAgentName(organizationId, "Pentest agent", null)).thenReturn(false);

        assertDoesNotThrow(() -> agentValidator.ensureNameFree(organizationId, "Pentest agent", null));
    }

    @Test
    void ensureNoNameConflict_nameIndexViolation_throwsConflictNameTaken() {
        DataIntegrityViolationException violation = integrityViolation(AgentConstant.AGENT_NAME_UNIQUE_INDEX);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> agentValidator.ensureNoNameConflict(violation, UUID.randomUUID(), null));

        assertEquals("AGT_STA_001", exception.getErrorCode());
    }

    @Test
    void ensureNoNameConflict_otherConstraint_passes() {
        DataIntegrityViolationException violation = integrityViolation("users_email_uk");

        assertDoesNotThrow(() -> agentValidator.ensureNoNameConflict(violation, UUID.randomUUID(), null));
    }

    private static DataIntegrityViolationException integrityViolation(String constraintName) {
        return new DataIntegrityViolationException("violation",
                new ConstraintViolationException("violation", new SQLException("duplicate"), constraintName));
    }

    private static User user(UserKind kind) {
        return User.builder().id(UUID.randomUUID()).kind(kind).build();
    }
}
