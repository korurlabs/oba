package com.korur.oba.apps.kaya.session.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.repository.SessionRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Opening the session of a batch: a new session locks its user before the row that references the user and
 * the machine is inserted, so the batch locks in the order a retirement of the user's machines uses, and a
 * batch of an existing session takes no lock on the user.
 */
@ExtendWith(MockitoExtension.class)
class SessionIngestServiceImplUnitTest {

    private static final String SESSION_ID = "sess-1";
    private static final Instant STARTED_AT = Instant.parse("2026-11-27T09:00:00Z");

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private KayaUserQueryService kayaUserQueryService;
    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private SessionIngestServiceImpl sessionIngestService;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID repositoryId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();

    @Test
    void openSession_existingSession_returnsItWithoutLockingTheUserOrInserting() {
        Session existing = session(userId);
        when(sessionRepository.findByOrganizationIdAndSessionIdForUpdate(organizationId, SESSION_ID))
                .thenReturn(Optional.of(existing));

        Session opened = sessionIngestService.openSession(organizationId, repositoryId, userId, machineId, SESSION_ID,
                STARTED_AT);

        assertSame(existing, opened);
        verifyNoInteractions(kayaUserQueryService);
        verify(sessionRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void openSession_newSession_locksTheUserBeforeInsertingTheRow() {
        Session created = session(userId);
        when(sessionRepository.findByOrganizationIdAndSessionIdForUpdate(organizationId, SESSION_ID))
                .thenReturn(Optional.empty(), Optional.of(created));
        when(sessionRepository.insertIfAbsent(organizationId, repositoryId, userId, machineId, SESSION_ID, STARTED_AT))
                .thenReturn(1);

        Session opened = sessionIngestService.openSession(organizationId, repositoryId, userId, machineId, SESSION_ID,
                STARTED_AT);

        assertSame(created, opened);
        InOrder order = inOrder(kayaUserQueryService, sessionRepository);
        order.verify(sessionRepository).findByOrganizationIdAndSessionIdForUpdate(organizationId, SESSION_ID);
        order.verify(kayaUserQueryService).lockUserForKeyShare(userId);
        order.verify(sessionRepository).insertIfAbsent(organizationId, repositoryId, userId, machineId, SESSION_ID,
                STARTED_AT);
        order.verify(sessionRepository).findByOrganizationIdAndSessionIdForUpdate(organizationId, SESSION_ID);
    }

    @Test
    void openSession_sessionOfAnotherUser_throwsConflictWithoutLockingTheUser() {
        when(sessionRepository.findByOrganizationIdAndSessionIdForUpdate(organizationId, SESSION_ID))
                .thenReturn(Optional.of(session(UUID.randomUUID())));

        ConflictException exception = assertThrows(ConflictException.class, () -> sessionIngestService.openSession(
                organizationId, repositoryId, userId, machineId, SESSION_ID, STARTED_AT));

        assertEquals("KAY_VAL_001", exception.getErrorCode());
        verifyNoInteractions(kayaUserQueryService);
    }

    private Session session(UUID owner) {
        return Session.builder().id(UUID.randomUUID()).organizationId(organizationId).userId(owner)
                .sessionId(SESSION_ID).build();
    }
}
