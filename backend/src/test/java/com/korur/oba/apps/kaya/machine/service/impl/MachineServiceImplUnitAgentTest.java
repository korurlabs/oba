package com.korur.oba.apps.kaya.machine.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.UserAuditRecorder;
import com.korur.oba.apps.kaya.machine.dto.request.MachineCreateRequest;
import com.korur.oba.apps.kaya.machine.dto.request.MachineUpdateRequest;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;
import com.korur.oba.apps.kaya.machine.mapper.MachineMapper;
import com.korur.oba.apps.kaya.machine.repository.MachineRepository;
import com.korur.oba.apps.kaya.machine.repository.MachineUsageSnapshotRepository;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.apps.kaya.machine.service.MachineUsageQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.dto.IssuedApiToken;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.service.ApiTokenQueryService;
import com.korur.oba.dojo.security.token.service.ApiTokenService;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

/**
 * Runs the machine changes through the real {@link AgentValidator}: an agent never mints a Kaya
 * credential itself, nor revokes or retires the machine its runs record with, while an admin may
 * register a machine for an agent and rotate its token, whose hosted runs record their sessions with it.
 * The agent's machines end with the agent.
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
                mock(UserAuditRecorder.class), userAccessHelper, apiTokenService, mock(ApiTokenQueryService.class),
                new AgentValidator(userQueryService, loggingService), mock(OrganizationQueryService.class),
                mock(MachineMapper.class), Clock.systemUTC(), loggingService);
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
    void createMachine_adminForAgent_issuesTheAgentsMachineToken() {
        User admin = user(UserKind.HUMAN);
        User agent = user(UserKind.AGENT);
        when(kayaUserQueryService.getMemberEntityForUpdate(organizationId, agent.getId())).thenReturn(agent);
        when(machineRepository.saveAndFlush(any(Machine.class))).thenAnswer(invocation -> {
            Machine machine = invocation.getArgument(0);
            machine.setId(UUID.randomUUID());
            return machine;
        });
        ApiToken token = ApiToken.builder().tokenPrefix("kyt_abcd").expiresAt(Instant.now()).build();
        token.setId(UUID.randomUUID());
        when(apiTokenService.issue(eq(ApiTokenKind.KAYA_MACHINE), any(UUID.class), eq(agent), eq(organizationId)))
                .thenReturn(new IssuedApiToken(token, "kyt_plaintext", null));

        machineService.createMachine(admin, organizationId, new MachineCreateRequest("agent-host", agent.getId()));

        verify(userAccessHelper).requireAdminOrSelf(admin, organizationId, agent.getId());
        verify(apiTokenService).issue(eq(ApiTokenKind.KAYA_MACHINE), any(UUID.class), eq(agent), eq(organizationId));
    }

    @Test
    void rotateToken_agentCaller_throwsForbiddenAndIssuesNothing() {
        User agent = user(UserKind.AGENT);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> machineService.rotateToken(agent, organizationId, UUID.randomUUID()));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verifyNoInteractions(machineRepository, apiTokenService);
    }

    @Test
    void rotateToken_adminForAgentsMachine_issuesTheAgentsMachineToken() {
        User admin = user(UserKind.HUMAN);
        User agent = user(UserKind.AGENT);
        Machine machine = machineOf(agent);
        when(machineRepository.findByIdAndOrganizationIdForUpdate(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));
        ApiToken token = ApiToken.builder().tokenPrefix("kyt_abcd").expiresAt(Instant.now()).build();
        token.setId(UUID.randomUUID());
        when(apiTokenService.issue(ApiTokenKind.KAYA_MACHINE, machine.getId(), agent, organizationId))
                .thenReturn(new IssuedApiToken(token, "kyt_plaintext", UUID.randomUUID()));

        machineService.rotateToken(admin, organizationId, machine.getId());

        verify(userAccessHelper).requireAdminOrSelf(admin, organizationId, agent.getId());
        verify(apiTokenService).issue(ApiTokenKind.KAYA_MACHINE, machine.getId(), agent, organizationId);
    }

    @Test
    void rotateToken_adminForAPersonsMachine_throwsForbiddenAndIssuesNothing() {
        User admin = user(UserKind.HUMAN);
        User person = user(UserKind.HUMAN);
        Machine machine = machineOf(person);
        when(machineRepository.findByIdAndOrganizationIdForUpdate(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> machineService.rotateToken(admin, organizationId, machine.getId()));

        assertEquals("GEN_SEC_003", exception.getErrorCode());
        verifyNoInteractions(userAccessHelper, apiTokenService);
    }

    @Test
    void retireAllOfUser_revokesEveryMachineTokenAndRetiresTheActiveMachines() {
        User agent = user(UserKind.AGENT);
        Machine withToken = machineOf(agent);
        Machine withoutToken = machineOf(agent);
        when(machineRepository.findByOrganizationIdAndUserIdForUpdate(organizationId, agent.getId()))
                .thenReturn(List.of(withToken, withoutToken));
        ApiToken token = ApiToken.builder().tokenPrefix("kyt_abcd").expiresAt(Instant.now()).build();
        token.setId(UUID.randomUUID());
        when(apiTokenService.revoke(ApiTokenKind.KAYA_MACHINE, withToken.getId())).thenReturn(Optional.of(token));
        when(apiTokenService.revoke(ApiTokenKind.KAYA_MACHINE, withoutToken.getId())).thenReturn(Optional.empty());
        when(machineRepository.retireActiveByOrganizationIdAndUserId(organizationId, agent.getId())).thenReturn(2);

        int retired = machineService.retireAllOfUser(organizationId, agent.getId());

        assertEquals(2, retired);
        InOrder order = inOrder(kayaUserQueryService, machineRepository);
        order.verify(kayaUserQueryService).getMemberEntityForUpdate(organizationId, agent.getId());
        order.verify(machineRepository).findByOrganizationIdAndUserIdForUpdate(organizationId, agent.getId());
        order.verify(machineRepository).retireActiveByOrganizationIdAndUserId(organizationId, agent.getId());
        verify(apiTokenService).revoke(ApiTokenKind.KAYA_MACHINE, withToken.getId());
        verify(apiTokenService).revoke(ApiTokenKind.KAYA_MACHINE, withoutToken.getId());
        verify(apiTokenService, never()).revoke(ApiTokenKind.AGENT, agent.getId());
    }

    @Test
    void revokeToken_agentCaller_throwsForbiddenAndRevokesNothing() {
        User agent = user(UserKind.AGENT);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> machineService.revokeToken(agent, organizationId, UUID.randomUUID()));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verifyNoInteractions(machineRepository, apiTokenService);
    }

    @Test
    void revokeToken_adminForAgentsMachine_revokesTheToken() {
        User admin = user(UserKind.HUMAN);
        User agent = user(UserKind.AGENT);
        Machine machine = Machine.builder().organizationId(organizationId).user(agent).machineName("cloud").build();
        machine.setId(UUID.randomUUID());
        when(machineRepository.findByIdAndOrganizationIdForUpdate(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));

        machineService.revokeToken(admin, organizationId, machine.getId());

        verify(userAccessHelper).requireAdminOrSelf(admin, organizationId, agent.getId());
        verify(apiTokenService).revoke(ApiTokenKind.KAYA_MACHINE, machine.getId());
    }

    @Test
    void updateMachine_agentRetires_throwsForbiddenAndRetiresNothing() {
        User agent = user(UserKind.AGENT);

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> machineService.updateMachine(
                agent, organizationId, UUID.randomUUID(), new MachineUpdateRequest(MachineStatus.RETIRED)));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verifyNoInteractions(machineRepository, apiTokenService);
    }

    private Machine machineOf(User owner) {
        Machine machine = Machine.builder().organizationId(organizationId).user(owner).machineName("cloud")
                .status(MachineStatus.ACTIVE).build();
        machine.setId(UUID.randomUUID());
        return machine;
    }

    private static User user(UserKind kind) {
        return User.builder().id(UUID.randomUUID()).kind(kind).build();
    }
}
