package com.korur.oba.apps.kaya.delivery.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.delivery.DeliveryProposalProperties;
import com.korur.oba.apps.kaya.delivery.dto.request.DeliveryProposalRequest;
import com.korur.oba.apps.kaya.delivery.mapper.DeliveryMapper;
import com.korur.oba.apps.kaya.delivery.service.DeliveryQueryService;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.render.enums.FileEncoding;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.dto.request.ProposalFileRequest;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.apps.kaya.version.service.VersionService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Runs a configuration proposal through the real {@link AgentValidator}: an approved proposal releases
 * hooks and settings to every machine of the organization, so an agent's machine token proposes
 * nothing, while a person's proposal goes to review as before.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryProposalServiceImplUnitAgentTest {

    private static final String REMOTE_URL = "git@github.com:acme/app.git";
    private static final String PATH = ".claude/settings.json";
    private static final String REASON = "Allow the board CLI";
    private static final int RELEASE_NUMBER = 4;

    @Mock
    private DeliveryQueryService deliveryQueryService;
    @Mock
    private ReleaseQueryService releaseQueryService;
    @Mock
    private PackQueryService packQueryService;
    @Mock
    private VersionService versionService;
    @Mock
    private VersionQueryService versionQueryService;
    @Mock
    private ValidationQueryService validationQueryService;
    @Mock
    private DeliveryMapper deliveryMapper;
    @Mock
    private KayaUserQueryService kayaUserQueryService;

    private DeliveryProposalServiceImpl service;
    private final UUID organizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private MachinePrincipal principal;

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        service = new DeliveryProposalServiceImpl(deliveryQueryService, releaseQueryService, packQueryService,
                versionService, versionQueryService, validationQueryService, deliveryMapper,
                new DeliveryProposalProperties("https://kaya.test/"), kayaUserQueryService,
                new AgentValidator(mock(UserQueryService.class), loggingService), loggingService);
        principal = new MachinePrincipal(organizationId, userId, UUID.randomUUID(), UUID.randomUUID(), "runner");
    }

    @Test
    void propose_agentMachineToken_throwsForbiddenBeforeAnyLookupOrWrite() {
        // Arrange
        givenUser(UserKind.AGENT);

        // Act
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> service.propose(principal, request()));

        // Assert
        assertEquals("AGT_SEC_002", exception.getErrorCode());
        assertEquals("KAYA_CONFIG_PROPOSE", exception.getErrorValues().get("action"));
        verifyNoInteractions(deliveryQueryService, releaseQueryService, packQueryService, versionService,
                versionQueryService, validationQueryService, deliveryMapper);
    }

    @Test
    void propose_personMachineToken_goesToReviewWithThePersonAsProposer() {
        // Arrange
        givenUser(UserKind.HUMAN);
        UUID repositoryId = UUID.randomUUID();
        UUID packId = UUID.randomUUID();
        when(deliveryQueryService.getRepository(organizationId, REMOTE_URL)).thenReturn(new RepositoryResponse(
                repositoryId, "app", REMOTE_URL, null, null, null, null));
        ReleaseEntry entry = new ReleaseEntry(PATH, "a".repeat(64), FileMode.FILE, null, 10, true, packId,
                UUID.randomUUID(), null, null, null);
        when(releaseQueryService.getEntityByNumber(organizationId, repositoryId, RELEASE_NUMBER)).thenReturn(
                Release.builder().id(UUID.randomUUID()).repositoryId(repositoryId).number(RELEASE_NUMBER)
                        .manifest(List.of(entry)).build());
        when(packQueryService.getEntityById(organizationId, packId)).thenReturn(
                Pack.builder().id(packId).organizationId(organizationId).name("shared").build());
        VersionResponse version = mock(VersionResponse.class);
        when(version.id()).thenReturn(UUID.randomUUID());
        when(versionService.propose(eq(userId), eq(organizationId), eq(packId), anyMap(), anyList(), eq(REASON)))
                .thenReturn(version);

        // Act
        service.propose(principal, request());

        // Assert
        verify(versionService).propose(eq(userId), eq(organizationId), eq(packId), anyMap(), anyList(), eq(REASON));
    }

    private void givenUser(UserKind kind) {
        when(kayaUserQueryService.getEntityById(userId)).thenReturn(User.builder().id(userId).kind(kind).build());
    }

    private static DeliveryProposalRequest request() {
        return new DeliveryProposalRequest(REMOTE_URL, RELEASE_NUMBER, REASON,
                List.of(new ProposalFileRequest(PATH, FileMode.FILE, FileEncoding.UTF_8, "{}\n", null, null)));
    }
}
