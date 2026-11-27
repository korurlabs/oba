package com.korur.oba.apps.kaya.machine.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.mapper.MachineMapper;
import com.korur.oba.apps.kaya.machine.repository.MachineRepository;
import com.korur.oba.apps.kaya.machine.service.MachineUsageQueryService;
import com.korur.oba.apps.kaya.session.service.SessionCountQueryService;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.service.ApiTokenQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

/**
 * The machine token chain checks no user, membership or organization on ingest, so the principal itself
 * refuses the machine of an agent that, or whose membership, is no longer ACTIVE, and every machine of an
 * organization that is not ACTIVE, while a person's machine in an ACTIVE organization resolves as before.
 */
@ExtendWith(MockitoExtension.class)
class MachineQueryServiceImplUnitAgentTest {

    @Mock
    private MachineRepository machineRepository;
    @Mock
    private MachineMapper machineMapper;
    @Mock
    private OrganizationQueryService organizationQueryService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    private MachineQueryServiceImpl machineQueryService;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        machineQueryService = new MachineQueryServiceImpl(machineRepository, mock(ApiTokenQueryService.class),
                mock(MachineUsageQueryService.class), mock(SessionCountQueryService.class), organizationQueryService,
                organizationMemberQueryService, machineMapper, Clock.systemUTC(), new LoggingService());
    }

    @Test
    void findActivePrincipal_disabledAgent_isEmpty() {
        Machine machine = machineOf(user(UserKind.AGENT, UserStatus.DISABLED));
        when(machineRepository.findByIdAndOrganizationId(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));

        Optional<MachinePrincipal> principal = machineQueryService.findActivePrincipal(organizationId, machine.getId(),
                tokenId);

        assertTrue(principal.isEmpty());
        verify(machineMapper, never()).toPrincipal(any(), any());
    }

    @Test
    void findActivePrincipal_activeAgent_returnsThePrincipal() {
        Machine machine = machineOf(user(UserKind.AGENT, UserStatus.ACTIVE));
        MachinePrincipal expected = principalOf(machine);
        when(machineRepository.findByIdAndOrganizationId(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));
        givenMembership(machine.getUser(), OrganizationMemberStatus.ACTIVE);
        when(organizationQueryService.isOrganizationActive(organizationId)).thenReturn(true);
        when(machineMapper.toPrincipal(machine, tokenId)).thenReturn(expected);

        Optional<MachinePrincipal> principal = machineQueryService.findActivePrincipal(organizationId, machine.getId(),
                tokenId);

        assertSame(expected, principal.orElseThrow());
    }

    @Test
    void findActivePrincipal_disabledPerson_returnsThePrincipalAsBefore() {
        Machine machine = machineOf(user(UserKind.HUMAN, UserStatus.DISABLED));
        MachinePrincipal expected = principalOf(machine);
        when(machineRepository.findByIdAndOrganizationId(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));
        when(organizationQueryService.isOrganizationActive(organizationId)).thenReturn(true);
        when(machineMapper.toPrincipal(machine, tokenId)).thenReturn(expected);

        Optional<MachinePrincipal> principal = machineQueryService.findActivePrincipal(organizationId, machine.getId(),
                tokenId);

        assertSame(expected, principal.orElseThrow());
    }

    @Test
    void findActivePrincipal_personInAnActiveOrganization_returnsThePrincipalWithoutReadingTheMembership() {
        Machine machine = machineOf(user(UserKind.HUMAN, UserStatus.ACTIVE));
        MachinePrincipal expected = principalOf(machine);
        when(machineRepository.findByIdAndOrganizationId(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));
        when(organizationQueryService.isOrganizationActive(organizationId)).thenReturn(true);
        when(machineMapper.toPrincipal(machine, tokenId)).thenReturn(expected);

        Optional<MachinePrincipal> principal = machineQueryService.findActivePrincipal(organizationId, machine.getId(),
                tokenId);

        assertSame(expected, principal.orElseThrow());
        verifyNoInteractions(organizationMemberQueryService);
    }

    @Test
    void findActivePrincipal_personInAnOrganizationThatIsNotActive_isEmpty() {
        Machine machine = machineOf(user(UserKind.HUMAN, UserStatus.ACTIVE));
        when(machineRepository.findByIdAndOrganizationId(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));
        when(organizationQueryService.isOrganizationActive(organizationId)).thenReturn(false);

        Optional<MachinePrincipal> principal = machineQueryService.findActivePrincipal(organizationId, machine.getId(),
                tokenId);

        assertTrue(principal.isEmpty());
        verify(machineMapper, never()).toPrincipal(any(), any());
    }

    @Test
    void findActivePrincipal_activeAgentInAnOrganizationThatIsNotActive_isEmpty() {
        Machine machine = machineOf(user(UserKind.AGENT, UserStatus.ACTIVE));
        when(machineRepository.findByIdAndOrganizationId(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));
        givenMembership(machine.getUser(), OrganizationMemberStatus.ACTIVE);
        when(organizationQueryService.isOrganizationActive(organizationId)).thenReturn(false);

        Optional<MachinePrincipal> principal = machineQueryService.findActivePrincipal(organizationId, machine.getId(),
                tokenId);

        assertTrue(principal.isEmpty());
        verify(machineMapper, never()).toPrincipal(any(), any());
    }

    @Test
    void findActivePrincipal_activeAgentWithADisabledMembership_isEmpty() {
        Machine machine = machineOf(user(UserKind.AGENT, UserStatus.ACTIVE));
        when(machineRepository.findByIdAndOrganizationId(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));
        givenMembership(machine.getUser(), OrganizationMemberStatus.DISABLED);

        Optional<MachinePrincipal> principal = machineQueryService.findActivePrincipal(organizationId, machine.getId(),
                tokenId);

        assertTrue(principal.isEmpty());
        verify(machineMapper, never()).toPrincipal(any(), any());
    }

    @Test
    void findActivePrincipal_activeAgentWithoutAMembership_isEmpty() {
        Machine machine = machineOf(user(UserKind.AGENT, UserStatus.ACTIVE));
        when(machineRepository.findByIdAndOrganizationId(machine.getId(), organizationId))
                .thenReturn(Optional.of(machine));
        when(organizationMemberQueryService.findMember(organizationId, machine.getUser().getId()))
                .thenReturn(Optional.empty());

        Optional<MachinePrincipal> principal = machineQueryService.findActivePrincipal(organizationId, machine.getId(),
                tokenId);

        assertTrue(principal.isEmpty());
        verify(machineMapper, never()).toPrincipal(any(), any());
    }

    private void givenMembership(User owner, OrganizationMemberStatus status) {
        OrganizationMember member = OrganizationMember.builder().user(owner).status(status).build();
        when(organizationMemberQueryService.findMember(organizationId, owner.getId())).thenReturn(Optional.of(member));
    }

    private Machine machineOf(User owner) {
        Machine machine = Machine.builder().organizationId(organizationId).user(owner).machineName("cloud").build();
        machine.setId(UUID.randomUUID());
        return machine;
    }

    private MachinePrincipal principalOf(Machine machine) {
        return new MachinePrincipal(organizationId, machine.getUser().getId(), machine.getId(), tokenId,
                machine.getMachineName());
    }

    private static User user(UserKind kind, UserStatus status) {
        return User.builder().id(UUID.randomUUID()).kind(kind).status(status).build();
    }
}
