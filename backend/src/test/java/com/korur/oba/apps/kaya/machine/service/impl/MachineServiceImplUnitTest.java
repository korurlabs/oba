package com.korur.oba.apps.kaya.machine.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.UserAuditRecorder;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;
import com.korur.oba.apps.kaya.machine.mapper.MachineMapper;
import com.korur.oba.apps.kaya.machine.repository.MachineRepository;
import com.korur.oba.apps.kaya.machine.repository.MachineUsageSnapshotRepository;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.apps.kaya.machine.service.MachineUsageQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.service.ApiTokenQueryService;
import com.korur.oba.dojo.security.token.service.ApiTokenService;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The recorder's contact with its machine: a batch locks the token's machine and is refused when the
 * machine was retired, or its token rotated or revoked, after the token was checked, or when its
 * organization is deleted or disabled, and the batch records itself on that locked instance. A disabled
 * organization gets the 403 the recorder retries, not the 401 after which it waits for a new token.
 */
@ExtendWith(MockitoExtension.class)
class MachineServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-27T09:00:00Z");

    @Mock
    private MachineRepository machineRepository;
    @Mock
    private ApiTokenQueryService apiTokenQueryService;
    @Mock
    private OrganizationQueryService organizationQueryService;

    private MachineServiceImpl machineService;
    private MachinePrincipal principal;

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        machineService = new MachineServiceImpl(machineRepository, mock(MachineQueryService.class),
                mock(MachineUsageQueryService.class), mock(MachineUsageSnapshotRepository.class),
                mock(KayaUserQueryService.class), mock(UserAuditRecorder.class), mock(UserAccessHelper.class),
                mock(ApiTokenService.class), apiTokenQueryService,
                new AgentValidator(mock(UserQueryService.class), loggingService), organizationQueryService,
                mock(MachineMapper.class), Clock.fixed(NOW, ZoneOffset.UTC), loggingService);
        principal = new MachinePrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "laptop");
    }

    @Nested
    class LockActiveMachine {

        @Test
        void activeMachine_isReturnedLocked() {
            Machine machine = machine(MachineStatus.ACTIVE);
            when(machineRepository.findByIdAndOrganizationIdForUpdate(principal.machineId(), principal.organizationId()))
                    .thenReturn(Optional.of(machine));
            when(apiTokenQueryService.findLive(ApiTokenKind.KAYA_MACHINE, List.of(principal.machineId())))
                    .thenReturn(List.of(token(principal.tokenId())));
            when(organizationQueryService.findOrganizationStatus(principal.organizationId()))
                    .thenReturn(Optional.of(OrganizationStatus.ACTIVE));

            Machine locked = machineService.lockActiveMachine(principal);

            assertSame(machine, locked);
        }

        @Test
        void organizationDisabled_throwsForbiddenOrganizationDisabled() {
            givenActiveMachineWithItsLiveToken();
            when(organizationQueryService.findOrganizationStatus(principal.organizationId()))
                    .thenReturn(Optional.of(OrganizationStatus.DISABLED));

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> machineService.lockActiveMachine(principal));

            assertEquals("ORG_SEC_005", exception.getErrorCode());
        }

        @Test
        void organizationDeleted_throwsUnauthorized() {
            givenActiveMachineWithItsLiveToken();
            when(organizationQueryService.findOrganizationStatus(principal.organizationId()))
                    .thenReturn(Optional.empty());

            UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                    () -> machineService.lockActiveMachine(principal));

            assertEquals("KAY_SEC_003", exception.getErrorCode());
        }

        @Test
        void tokenRotatedAfterItWasChecked_throwsUnauthorized() {
            when(machineRepository.findByIdAndOrganizationIdForUpdate(principal.machineId(), principal.organizationId()))
                    .thenReturn(Optional.of(machine(MachineStatus.ACTIVE)));
            when(apiTokenQueryService.findLive(ApiTokenKind.KAYA_MACHINE, List.of(principal.machineId())))
                    .thenReturn(List.of(token(UUID.randomUUID())));

            UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                    () -> machineService.lockActiveMachine(principal));

            assertEquals("KAY_SEC_003", exception.getErrorCode());
        }

        @Test
        void tokenRevokedAfterItWasChecked_throwsUnauthorized() {
            when(machineRepository.findByIdAndOrganizationIdForUpdate(principal.machineId(), principal.organizationId()))
                    .thenReturn(Optional.of(machine(MachineStatus.ACTIVE)));
            when(apiTokenQueryService.findLive(ApiTokenKind.KAYA_MACHINE, List.of(principal.machineId())))
                    .thenReturn(List.of());

            UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                    () -> machineService.lockActiveMachine(principal));

            assertEquals("KAY_SEC_003", exception.getErrorCode());
        }

        @Test
        void machineRetiredAfterItsTokenWasChecked_throwsUnauthorized() {
            when(machineRepository.findByIdAndOrganizationIdForUpdate(principal.machineId(), principal.organizationId()))
                    .thenReturn(Optional.of(machine(MachineStatus.RETIRED)));

            UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                    () -> machineService.lockActiveMachine(principal));

            assertEquals("KAY_SEC_003", exception.getErrorCode());
        }

        @Test
        void machineNotInTheTokensOrganization_throwsUnauthorized() {
            when(machineRepository.findByIdAndOrganizationIdForUpdate(principal.machineId(), principal.organizationId()))
                    .thenReturn(Optional.empty());

            UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                    () -> machineService.lockActiveMachine(principal));

            assertEquals("KAY_SEC_003", exception.getErrorCode());
        }
    }

    @Nested
    class RecordSeen {

        @Test
        void changedVersion_savesTheGivenInstanceWithoutLoadingTheMachineAgain() {
            Machine machine = machine(MachineStatus.ACTIVE);
            machine.setClaudeCodeVersion("2.0.0");
            machine.setLastSeenAt(NOW);
            when(machineRepository.saveAndFlush(machine)).thenReturn(machine);

            Machine recorded = machineService.recordSeen(machine, "2.1.0", null);

            assertSame(machine, recorded);
            assertEquals("2.1.0", machine.getClaudeCodeVersion());
            assertEquals(MachineStatus.ACTIVE, machine.getStatus());
            verify(machineRepository, never()).findByIdAndOrganizationId(any(), any());
        }

        @Test
        void unchangedAndRecentlySeen_writesNothing() {
            Machine machine = machine(MachineStatus.ACTIVE);
            machine.setClaudeCodeVersion("2.0.0");
            machine.setLastSeenAt(NOW);

            Machine recorded = machineService.recordSeen(machine, "2.0.0", null);

            assertSame(machine, recorded);
            verify(machineRepository, never()).saveAndFlush(any());
        }
    }

    private void givenActiveMachineWithItsLiveToken() {
        when(machineRepository.findByIdAndOrganizationIdForUpdate(principal.machineId(), principal.organizationId()))
                .thenReturn(Optional.of(machine(MachineStatus.ACTIVE)));
        when(apiTokenQueryService.findLive(ApiTokenKind.KAYA_MACHINE, List.of(principal.machineId())))
                .thenReturn(List.of(token(principal.tokenId())));
    }

    private static ApiToken token(UUID tokenId) {
        ApiToken token = ApiToken.builder().kind(ApiTokenKind.KAYA_MACHINE).build();
        token.setId(tokenId);
        return token;
    }

    private Machine machine(MachineStatus status) {
        Machine machine = Machine.builder().organizationId(principal.organizationId()).machineName("laptop")
                .status(status).build();
        machine.setId(principal.machineId());
        return machine;
    }
}
