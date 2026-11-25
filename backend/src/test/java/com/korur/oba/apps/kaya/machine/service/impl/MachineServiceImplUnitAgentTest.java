package com.korur.oba.apps.kaya.machine.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.UserAuditRecorder;
import com.korur.oba.apps.kaya.machine.dto.request.MachineCreateRequest;
import com.korur.oba.apps.kaya.machine.mapper.MachineMapper;
import com.korur.oba.apps.kaya.machine.repository.MachineRepository;
import com.korur.oba.apps.kaya.machine.repository.MachineUsageSnapshotRepository;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.apps.kaya.machine.service.MachineUsageQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.service.ApiTokenService;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Runs machine registration and token rotation through the real {@link AgentValidator}: an agent
 * never mints a Kaya credential, neither for itself nor through an admin registering one for it.
 */
@ExtendWith(MockitoExtension.class)
class MachineServiceImplUnitAgentTest {

    @Mock
    private MachineRepository machineRepository;
    @Mock
    private KayaUserQueryService kayaUserQueryService;
    @Mock
    private UserAccessHelper userAccessHelper;
    @Mock
    private ApiTokenService apiTokenService;
    @Mock
    private UserQueryService userQueryService;

    private MachineServiceImpl machineService;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        machineService = new MachineServiceImpl(machineRepository, mock(MachineQueryService.class),
                mock(MachineUsageQueryService.class),
                mock(MachineUsageSnapshotRepository.class), kayaUserQueryService,
                mock(UserAuditRecorder.class), userAccessHelper, apiTokenService,
                new AgentValidator(userQueryService, loggingService), mock(MachineMapper.class),
                Clock.systemUTC(), loggingService);
    }

    @Test
    void createMachine_agentCaller_throwsForbiddenAndCreatesNothing() {
        User agent = user(UserKind.AGENT);

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> machineService.createMachine(
                agent, organizationId, new MachineCreateRequest("agent-host", null)));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verifyNoInteractions(kayaUserQueryService, machineRepository, apiTokenService);
    }

    @Test
    void createMachine_adminForAgent_throwsAgentTargetRefusedAndCreatesNothing() {
        User admin = user(UserKind.HUMAN);
        User agent = user(UserKind.AGENT);
        when(kayaUserQueryService.getMemberEntityForUpdate(organizationId, agent.getId())).thenReturn(agent);

        ValidationException exception = assertThrows(ValidationException.class, () -> machineService.createMachine(
                admin, organizationId, new MachineCreateRequest("agent-host", agent.getId())));

        assertEquals("AGT_VAL_001", exception.getErrorCode());
        verifyNoInteractions(machineRepository, apiTokenService);
    }

    @Test
    void rotateToken_agentCaller_throwsForbiddenAndIssuesNothing() {
        User agent = user(UserKind.AGENT);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> machineService.rotateToken(agent, organizationId, UUID.randomUUID()));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verifyNoInteractions(machineRepository, apiTokenService);
    }

    private static User user(UserKind kind) {
        return User.builder().id(UUID.randomUUID()).kind(kind).build();
    }
}
