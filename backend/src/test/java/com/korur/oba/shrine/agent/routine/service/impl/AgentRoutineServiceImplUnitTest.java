package com.korur.oba.shrine.agent.routine.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;
import com.korur.oba.connect.routine.core.exception.ExceptionDomainRoutineError;
import com.korur.oba.connect.routine.core.service.RoutineService;
import com.korur.oba.connect.routine.core.validator.RoutineValidator;
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.service.AESEncryptionService;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.agent.helper.AgentFlowHelper;
import com.korur.oba.shrine.agent.routine.dto.request.AgentRoutineSetRequest;
import com.korur.oba.shrine.agent.routine.entity.AgentRoutine;
import com.korur.oba.shrine.agent.routine.helper.AgentRoutineFireRecorder;
import com.korur.oba.shrine.agent.routine.repository.AgentRoutineRepository;
import com.korur.oba.shrine.agent.routine.service.AgentRoutineQueryService;
import com.korur.oba.shrine.agent.service.AgentQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.service.UserQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRoutineServiceImplUnitTest {

    private static final String SECRET = "sk-ant-oat01-SecretFireTokenValue0123456789";
    private static final String ROUTINE_ID = "trig_01AbCdEf";
    private static final String TICKET_KEY = "KAZ-42";
    private static final Instant NOW = Instant.parse("2026-11-25T09:00:00Z");

    @Mock
    private AgentRoutineRepository agentRoutineRepository;
    @Mock
    private AgentRoutineQueryService agentRoutineQueryService;
    @Mock
    private AgentQueryService agentQueryService;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private RoutineValidator routineValidator;
    @Mock
    private RoutineService routineService;
    @Mock
    private AgentRoutineFireRecorder agentRoutineFireRecorder;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private LoggingService loggingService;

    private final AESEncryptionService aesEncryptionService = new AESEncryptionService("unit-test-encryption-secret-0123");

    private AgentRoutineServiceImpl agentRoutineService;
    private User actor;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        agentRoutineService = new AgentRoutineServiceImpl(agentRoutineRepository, agentRoutineQueryService,
                agentQueryService, userQueryService, new AgentFlowHelper(), routineValidator, routineService,
                aesEncryptionService, agentRoutineFireRecorder, auditLogMapper, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC), loggingService);
        actor = User.builder().id(UUID.randomUUID()).email("admin@example.com").build();
        organizationId = UUID.randomUUID();
    }

    // ---------- set ----------

    @Test
    void set_routineAgentWithoutRoutine_savesEncryptedSecretAndPrefixAndAudits() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(agentRoutineQueryService.find(organizationId, agent.getId())).thenReturn(Optional.empty());
        when(agentRoutineRepository.save(any(AgentRoutine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AgentResponse expected = response(agent.getId());
        when(agentQueryService.getAgent(actor, organizationId, agent.getId())).thenReturn(expected);

        AgentResponse result = agentRoutineService.set(actor, organizationId, agent.getId(),
                new AgentRoutineSetRequest(" " + ROUTINE_ID + " ", " " + SECRET + " "));

        assertSame(expected, result);
        verify(routineValidator).ensureValidRoutineId(RoutineProvider.ANTHROPIC, ROUTINE_ID);
        ArgumentCaptor<AgentRoutine> saved = ArgumentCaptor.forClass(AgentRoutine.class);
        verify(agentRoutineRepository).save(saved.capture());
        AgentRoutine routine = saved.getValue();
        assertEquals(organizationId, routine.getOrganizationId());
        assertEquals(agent.getId(), routine.getUserId());
        assertEquals(ROUTINE_ID, routine.getRoutineId());
        assertEquals("sk-ant-oat01", routine.getRoutineSecretPrefix());
        assertNotEquals(SECRET, routine.getRoutineSecretEncrypted());
        assertEquals(SECRET, aesEncryptionService.decrypt(routine.getRoutineSecretEncrypted()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> newValues = ArgumentCaptor.forClass(Map.class);
        verify(auditLogMapper).toRequest(eq(organizationId), eq(actor.getId()), eq("AGENT_ROUTINE_SET"), eq("Agent"),
                eq(agent.getId()), isNull(), newValues.capture());
        assertEquals(ROUTINE_ID, newValues.getValue().get("routineId"));
        assertFalse(newValues.getValue().toString().contains(SECRET));
        verify(auditService).log(any());
        assertSecretNeverLogged();
    }

    @Test
    void set_existingRoutine_replacesIdAndSecretAndClearsLastFire() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        AgentRoutine existing = routine(agent.getId());
        existing.setLastFiredAt(NOW);
        existing.setLastFireStatus(RoutineFireStatus.UNAUTHORIZED);
        existing.setLastHttpStatus(401);
        existing.setLastSessionUrl("https://claude.ai/code/session_old");
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(agentRoutineQueryService.find(organizationId, agent.getId())).thenReturn(Optional.of(existing));
        when(agentRoutineRepository.save(existing)).thenReturn(existing);

        agentRoutineService.set(actor, organizationId, agent.getId(), new AgentRoutineSetRequest("trig_New01", SECRET));

        assertEquals("trig_New01", existing.getRoutineId());
        assertEquals(SECRET, aesEncryptionService.decrypt(existing.getRoutineSecretEncrypted()));
        assertNull(existing.getLastFiredAt());
        assertNull(existing.getLastFireStatus());
        assertNull(existing.getLastHttpStatus());
        assertNull(existing.getLastSessionUrl());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> oldValues = ArgumentCaptor.forClass(Map.class);
        verify(auditLogMapper).toRequest(eq(organizationId), eq(actor.getId()), eq("AGENT_ROUTINE_SET"), eq("Agent"),
                eq(agent.getId()), oldValues.capture(), any());
        assertEquals(ROUTINE_ID, oldValues.getValue().get("routineId"));
    }

    @Test
    void set_shortSecret_showsAtMostHalfOfIt() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(agentRoutineQueryService.find(organizationId, agent.getId())).thenReturn(Optional.empty());
        when(agentRoutineRepository.save(any(AgentRoutine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        agentRoutineService.set(actor, organizationId, agent.getId(), new AgentRoutineSetRequest(ROUTINE_ID, "abcdefgh"));

        ArgumentCaptor<AgentRoutine> saved = ArgumentCaptor.forClass(AgentRoutine.class);
        verify(agentRoutineRepository).save(saved.capture());
        assertEquals("abcd", saved.getValue().getRoutineSecretPrefix());
    }

    @Test
    void set_apiAgent_refusesWithFlowCodeAndSavesNothing() {
        for (AgentProvider provider : AgentProvider.values()) {
            User agent = agent(AgentFlow.API, provider, UserStatus.ACTIVE);
            when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> agentRoutineService.set(actor, organizationId, agent.getId(),
                            new AgentRoutineSetRequest(ROUTINE_ID, SECRET)));

            assertEquals("AGT_VAL_003", exception.getErrorCode());
        }
        verifyNoInteractions(routineValidator, agentRoutineRepository, auditService);
    }

    @Test
    void set_malformedRoutineId_answersAgentRoutineIdCode() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        doThrow(new ValidationException(ExceptionDomainRoutineError.VAL_ROUTINE_ID_FORMAT,
                Map.of("provider", "ANTHROPIC")))
                .when(routineValidator).ensureValidRoutineId(RoutineProvider.ANTHROPIC, "not-a-trigger");

        ValidationException exception = assertThrows(ValidationException.class,
                () -> agentRoutineService.set(actor, organizationId, agent.getId(),
                        new AgentRoutineSetRequest("not-a-trigger", SECRET)));

        assertEquals("AGT_VAL_002", exception.getErrorCode());
        assertEquals("ANTHROPIC", exception.getErrorValues().get("provider"));
        verifyNoInteractions(agentRoutineRepository, auditService);
    }

    // ---------- remove ----------

    @Test
    void remove_withRoutine_deletesAndAudits() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        AgentRoutine routine = routine(agent.getId());
        when(agentRoutineQueryService.find(organizationId, agent.getId())).thenReturn(Optional.of(routine));

        agentRoutineService.remove(actor, organizationId, agent.getId());

        verify(agentQueryService).getAgentEntity(organizationId, agent.getId());
        verify(agentRoutineRepository).delete(routine);
        verify(auditLogMapper).toRequest(eq(organizationId), eq(actor.getId()), eq("AGENT_ROUTINE_REMOVED"),
                eq("Agent"), eq(agent.getId()), any(), isNull());
    }

    @Test
    void remove_withoutRoutine_changesNothing() {
        UUID agentId = UUID.randomUUID();
        when(agentRoutineQueryService.find(organizationId, agentId)).thenReturn(Optional.empty());

        agentRoutineService.remove(actor, organizationId, agentId);

        verify(agentRoutineRepository, never()).delete(any());
        verifyNoInteractions(auditService, auditLogMapper);
    }

    // ---------- fire ----------

    @Test
    void fire_personOrOtherOrganization_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(userQueryService.findAgent(organizationId, userId)).thenReturn(Optional.empty());

        assertTrue(agentRoutineService.fire(organizationId, userId, TICKET_KEY).isEmpty());
        verifyNoInteractions(routineService, agentRoutineFireRecorder);
    }

    @Test
    void fire_apiAgent_returnsEmpty() {
        User agent = agent(AgentFlow.API, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        when(userQueryService.findAgent(organizationId, agent.getId())).thenReturn(Optional.of(agent));

        assertTrue(agentRoutineService.fire(organizationId, agent.getId(), TICKET_KEY).isEmpty());
        verifyNoInteractions(routineService, agentRoutineFireRecorder);
    }

    @Test
    void fire_disabledAgent_returnsEmpty() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.DISABLED);
        when(userQueryService.findAgent(organizationId, agent.getId())).thenReturn(Optional.of(agent));

        assertTrue(agentRoutineService.fire(organizationId, agent.getId(), TICKET_KEY).isEmpty());
        verifyNoInteractions(routineService, agentRoutineFireRecorder);
    }

    @Test
    void fire_agentWithoutRoutine_returnsEmpty() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        when(userQueryService.findAgent(organizationId, agent.getId())).thenReturn(Optional.of(agent));
        when(agentRoutineQueryService.find(organizationId, agent.getId())).thenReturn(Optional.empty());

        assertTrue(agentRoutineService.fire(organizationId, agent.getId(), TICKET_KEY).isEmpty());
        verifyNoInteractions(routineService, agentRoutineFireRecorder);
    }

    @Test
    void fire_routineAgent_firesWithTicketKeyAndRecordsResult() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        AgentRoutine routine = routine(agent.getId());
        RoutineFireResult fired = new RoutineFireResult(RoutineFireStatus.FIRED, 200, "https://claude.ai/code/session_01");
        when(userQueryService.findAgent(organizationId, agent.getId())).thenReturn(Optional.of(agent));
        when(agentRoutineQueryService.find(organizationId, agent.getId())).thenReturn(Optional.of(routine));
        when(routineService.fire(RoutineProvider.ANTHROPIC, ROUTINE_ID, SECRET, TICKET_KEY)).thenReturn(fired);

        Optional<RoutineFireResult> result = agentRoutineService.fire(organizationId, agent.getId(), TICKET_KEY);

        assertEquals(Optional.of(fired), result);
        verify(agentRoutineFireRecorder).record(routine.getId(), fired, NOW);
        assertSecretNeverLogged();
    }

    @Test
    void fire_vendorRefuses_recordsFailureAndReturnsIt() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        AgentRoutine routine = routine(agent.getId());
        RoutineFireResult refused = new RoutineFireResult(RoutineFireStatus.UNAUTHORIZED, 401, null);
        when(userQueryService.findAgent(organizationId, agent.getId())).thenReturn(Optional.of(agent));
        when(agentRoutineQueryService.find(organizationId, agent.getId())).thenReturn(Optional.of(routine));
        when(routineService.fire(RoutineProvider.ANTHROPIC, ROUTINE_ID, SECRET, TICKET_KEY)).thenReturn(refused);

        Optional<RoutineFireResult> result = agentRoutineService.fire(organizationId, agent.getId(), TICKET_KEY);

        assertEquals(Optional.of(refused), result);
        verify(agentRoutineFireRecorder).record(routine.getId(), refused, NOW);
        verify(loggingService).error(any(), any(), anyString(), eq(organizationId), eq(agent.getId()),
                eq(RoutineProvider.ANTHROPIC), eq(ROUTINE_ID), eq(RoutineFireStatus.UNAUTHORIZED), eq(401));
        assertSecretNeverLogged();
    }

    @Test
    void fire_connectorThrows_recordsFailedWithoutAnswer() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        AgentRoutine routine = routine(agent.getId());
        when(userQueryService.findAgent(organizationId, agent.getId())).thenReturn(Optional.of(agent));
        when(agentRoutineQueryService.find(organizationId, agent.getId())).thenReturn(Optional.of(routine));
        when(routineService.fire(RoutineProvider.ANTHROPIC, ROUTINE_ID, SECRET, TICKET_KEY))
                .thenThrow(new IllegalStateException("connector fault"));

        Optional<RoutineFireResult> result = agentRoutineService.fire(organizationId, agent.getId(), TICKET_KEY);

        assertEquals(Optional.of(RoutineFireResult.failedWithoutAnswer()), result);
        verify(agentRoutineFireRecorder).record(routine.getId(), RoutineFireResult.failedWithoutAnswer(), NOW);
    }

    @Test
    void fire_secretNoLongerDecrypts_recordsFailedWithoutCall() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, UserStatus.ACTIVE);
        AgentRoutine routine = routine(agent.getId());
        routine.setRoutineSecretEncrypted("bm90LWEtdmFsaWQtY2lwaGVydGV4dA==");
        when(userQueryService.findAgent(organizationId, agent.getId())).thenReturn(Optional.of(agent));
        when(agentRoutineQueryService.find(organizationId, agent.getId())).thenReturn(Optional.of(routine));

        Optional<RoutineFireResult> result = agentRoutineService.fire(organizationId, agent.getId(), TICKET_KEY);

        assertEquals(RoutineFireStatus.FAILED, result.orElseThrow().status());
        verifyNoInteractions(routineService);
        verify(agentRoutineFireRecorder).record(routine.getId(), RoutineFireResult.failedWithoutAnswer(), NOW);
    }

    private void assertSecretNeverLogged() {
        for (Invocation invocation : Mockito.mockingDetails(loggingService).getInvocations()) {
            assertFalse(Arrays.deepToString(invocation.getArguments()).contains(SECRET),
                    "secret logged by " + invocation.getMethod().getName());
        }
    }

    private AgentRoutine routine(UUID agentId) {
        return AgentRoutine.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .userId(agentId)
                .routineId(ROUTINE_ID)
                .routineSecretEncrypted(aesEncryptionService.encrypt(SECRET))
                .routineSecretPrefix("sk-ant-oat01")
                .build();
    }

    private User agent(AgentFlow flow, AgentProvider provider, UserStatus status) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("agent-x@agents.oba.invalid")
                .status(status)
                .kind(UserKind.AGENT)
                .profile(UserProfile.builder().firstName("Agent").build())
                .agentDetail(UserAgentDetail.builder()
                        .role(AgentRole.CYBERSECURITY_ENGINEER)
                        .flow(flow)
                        .provider(provider)
                        .organizationId(organizationId)
                        .build())
                .build();
    }

    private static AgentResponse response(UUID agentId) {
        return new AgentResponse(agentId, "Agent", AgentFlow.ROUTINE, AgentProvider.ANTHROPIC,
                AgentRole.CYBERSECURITY_ENGINEER, null, UserStatus.ACTIVE, null, null, null, ROUTINE_ID,
                "sk-ant-oat01", null, null, null);
    }
}
