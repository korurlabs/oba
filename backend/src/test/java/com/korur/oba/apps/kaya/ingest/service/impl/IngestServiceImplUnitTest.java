package com.korur.oba.apps.kaya.ingest.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.account.service.AccountService;
import com.korur.oba.apps.kaya.identity.service.UserGitEmailQueryService;
import com.korur.oba.apps.kaya.ingest.constant.helper.IngestTextSanitizerLogConstant;
import com.korur.oba.apps.kaya.ingest.constant.service.IngestServiceLogConstant;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestBatchRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestHandoffRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestMachineRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestRepositoryRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestSummaryRequest;
import com.korur.oba.apps.kaya.ingest.dto.response.IngestBatchResponse;
import com.korur.oba.apps.kaya.ingest.helper.IngestRateLimiter;
import com.korur.oba.apps.kaya.ingest.helper.IngestTextSanitizer;
import com.korur.oba.apps.kaya.ingest.mapper.IngestMapper;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.service.MachineService;
import com.korur.oba.apps.kaya.plan.service.PlanIngestService;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.service.RepositoryService;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.service.SessionIngestService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-20T09:00:00Z");
    private static final String SESSION_ID = "session-1";
    private static final String MACHINE_NAME = "laptop";
    private static final String REMOTE_URL = "git@github.com:example/app.git";
    private static final String REPOSITORY_NAME = "app";
    private static final String GIT_EMAIL = "ada@former-domain.example";

    @Mock
    private IngestRateLimiter ingestRateLimiter;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private MachineService machineService;
    @Mock
    private AccountService accountService;
    @Mock
    private UserGitEmailQueryService userGitEmailQueryService;
    @Mock
    private SessionIngestService sessionIngestService;
    @Mock
    private PlanIngestService planIngestService;
    @Mock
    private IngestMapper ingestMapper;
    @Mock
    private LoggingService loggingService;

    private IngestServiceImpl service;
    private MachinePrincipal principal;
    private Repository repository;
    private Session session;

    @BeforeEach
    void setUp() {
        // The real sanitizer: a batch without U+0000 passes it unchanged, as the stubs below expect.
        IngestTextSanitizer ingestTextSanitizer = new IngestTextSanitizer(loggingService);
        service = new IngestServiceImpl(ingestRateLimiter, ingestTextSanitizer, repositoryService, machineService, accountService,
                userGitEmailQueryService, sessionIngestService, planIngestService, ingestMapper,
                Clock.fixed(NOW, ZoneOffset.UTC), loggingService);
        principal = new MachinePrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MACHINE_NAME);
        repository = Repository.builder().id(UUID.randomUUID()).build();
        session = Session.builder().id(UUID.randomUUID()).build();
    }

    private IngestBatchRequest batch(String gitAuthorEmail) {
        IngestSummaryRequest summary = new IngestSummaryRequest(null, null, null, NOW, null, null, null, null, null,
                null, null, null, null, null, gitAuthorEmail, null);
        Map<String, Object> event = Map.of("seq", 1, "at", NOW.toString(), "event", "SessionStart");
        return new IngestBatchRequest(SESSION_ID, new IngestRepositoryRequest(REMOTE_URL, REPOSITORY_NAME),
                new IngestMachineRequest(MACHINE_NAME, null, null), summary, List.of(event), null, null, null);
    }

    /**
     * Stubs the writes of an accepted batch: the session is opened for the token's user and the
     * batch is applied with the given conflict user, so any other user or conflict fails the test.
     */
    private IngestBatchResponse stubAcceptedBatch(IngestBatchRequest request, UUID conflictUserId) {
        when(ingestRateLimiter.tryCharge(principal.tokenId())).thenReturn(true);
        when(repositoryService.connectRepository(principal.organizationId(), REMOTE_URL, REPOSITORY_NAME))
                .thenReturn(repository);
        when(sessionIngestService.openSession(principal.organizationId(), repository.getId(), principal.userId(),
                principal.machineId(), SESSION_ID, NOW)).thenReturn(session);
        when(sessionIngestService.applyBatch(session, request, null, conflictUserId)).thenReturn(1L);
        IngestBatchResponse response = new IngestBatchResponse(SESSION_ID, 1L);
        when(ingestMapper.toBatchResponse(SESSION_ID, 1L)).thenReturn(response);
        return response;
    }

    @Nested
    class GitEmail {

        @Test
        void unknownGitEmail_isAcceptedForTheTokensUserWithoutAConflict() {
            IngestBatchRequest request = batch(GIT_EMAIL);
            when(userGitEmailQueryService.findUserIdByEmail(principal.organizationId(), GIT_EMAIL))
                    .thenReturn(Optional.empty());
            IngestBatchResponse expected = stubAcceptedBatch(request, null);

            IngestBatchResponse response = service.ingestBatch(principal, request);

            assertSame(expected, response);
            verify(loggingService).debug(any(), eq(LogLayer.SERVICE), eq(IngestServiceLogConstant.GIT_EMAIL_UNKNOWN),
                    eq(SESSION_ID), eq(principal.machineId()), eq(principal.organizationId()));
            verify(loggingService, never()).warn(any(), any(), any(String.class), any(Object[].class));
        }

        @Test
        void gitEmailOfAnotherMember_isAcceptedForTheTokensUserAndFlagsThatMember() {
            IngestBatchRequest request = batch(GIT_EMAIL);
            UUID otherMemberId = UUID.randomUUID();
            when(userGitEmailQueryService.findUserIdByEmail(principal.organizationId(), GIT_EMAIL))
                    .thenReturn(Optional.of(otherMemberId));
            IngestBatchResponse expected = stubAcceptedBatch(request, otherMemberId);

            IngestBatchResponse response = service.ingestBatch(principal, request);

            assertSame(expected, response);
            verify(loggingService).info(any(), eq(LogLayer.SERVICE), eq(IngestServiceLogConstant.IDENTITY_CONFLICT),
                    eq(SESSION_ID), eq(otherMemberId), eq(principal.userId()));
        }

        @Test
        void gitEmailOfTheTokensUser_isNoConflict() {
            IngestBatchRequest request = batch(GIT_EMAIL);
            when(userGitEmailQueryService.findUserIdByEmail(principal.organizationId(), GIT_EMAIL))
                    .thenReturn(Optional.of(principal.userId()));
            IngestBatchResponse expected = stubAcceptedBatch(request, null);

            IngestBatchResponse response = service.ingestBatch(principal, request);

            assertSame(expected, response);
        }

        @Test
        void batchWithoutGitEmail_looksUpNoHolderAndIsNoConflict() {
            IngestBatchRequest request = batch(null);
            IngestBatchResponse expected = stubAcceptedBatch(request, null);

            IngestBatchResponse response = service.ingestBatch(principal, request);

            assertSame(expected, response);
            verifyNoInteractions(userGitEmailQueryService);
        }
    }

    @Nested
    class MachineLock {

        @Test
        void activeMachine_isLockedAfterTheSessionIsWrittenAndRecordedAsTheLockedInstance() {
            IngestBatchRequest request = batch(null);
            IngestBatchResponse expected = stubAcceptedBatch(request, null);
            Machine locked = Machine.builder().machineName(MACHINE_NAME).build();
            when(machineService.lockActiveMachine(principal)).thenReturn(locked);

            IngestBatchResponse response = service.ingestBatch(principal, request);

            assertSame(expected, response);
            InOrder order = inOrder(sessionIngestService, planIngestService, machineService);
            order.verify(sessionIngestService).applyBatch(session, request, null, null);
            order.verify(planIngestService).storePlans(session, null);
            order.verify(machineService).lockActiveMachine(principal);
            order.verify(machineService).recordSeen(locked, null, null);
        }

        @Test
        void machineRetiredAfterItsTokenWasChecked_refusesTheBatchAndRecordsNothingOnTheMachine() {
            IngestBatchRequest request = batch(null);
            when(ingestRateLimiter.tryCharge(principal.tokenId())).thenReturn(true);
            when(repositoryService.connectRepository(principal.organizationId(), REMOTE_URL, REPOSITORY_NAME))
                    .thenReturn(repository);
            when(sessionIngestService.openSession(principal.organizationId(), repository.getId(), principal.userId(),
                    principal.machineId(), SESSION_ID, NOW)).thenReturn(session);
            when(machineService.lockActiveMachine(principal))
                    .thenThrow(new UnauthorizedException(ExceptionDomainKayaError.SEC_MACHINE_TOKEN_INVALID));

            UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                    () -> service.ingestBatch(principal, request));

            assertEquals("KAY_SEC_003", exception.getErrorCode());
            verify(machineService, never()).recordSeen(any(), any(), any());
            verify(machineService, never()).recordUsage(any(), any(), any(), any());
            verifyNoInteractions(ingestMapper);
        }
    }

    @Nested
    class RateLimit {

        @Test
        void batchOverTheTokensBudget_isRefusedBeforeItsTextIsSanitizedOrStored() {
            IngestBatchRequest received = batch(null);
            IngestBatchRequest request = new IngestBatchRequest(SESSION_ID, received.repository(), received.machine(),
                    received.summary(), received.events(),
                    List.of(new IngestHandoffRequest("export/screen.png", "\uFFFDPNG\u0000\u0000", NOW)), null, null);
            when(ingestRateLimiter.tryCharge(principal.tokenId())).thenReturn(false);

            assertThrows(TooManyRequestsException.class, () -> service.ingestBatch(principal, request));

            verify(loggingService, never()).warn(any(), any(), eq(IngestTextSanitizerLogConstant.HANDOFF_SKIPPED),
                    any(Object[].class));
            verifyNoInteractions(repositoryService, machineService, sessionIngestService, planIngestService);
        }
    }
}
