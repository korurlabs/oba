package com.korur.oba.apps.kaya.session.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.mapper.SessionMapper;
import com.korur.oba.apps.kaya.session.repository.SessionLinkRepository;
import com.korur.oba.apps.kaya.session.repository.SessionRepository;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Runs session deletion through the real {@link AgentValidator}: the transcript is the record of what a
 * run did, so an agent never deletes one, not even its own.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceImplUnitAgentTest {

    private static final String SESSION_ID = "sess-1";

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionQueryService sessionQueryService;
    @Mock
    private UserAccessHelper userAccessHelper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserQueryService userQueryService;

    private SessionServiceImpl sessionService;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        sessionService = new SessionServiceImpl(sessionRepository, mock(SessionLinkRepository.class),
                sessionQueryService, userAccessHelper, new AgentValidator(userQueryService, loggingService),
                mock(RepositoryQueryService.class), mock(SessionMapper.class), eventPublisher, loggingService);
    }

    @Test
    void deleteSession_agentCaller_throwsForbiddenAndDeletesNothing() {
        User agent = user(UserKind.AGENT);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> sessionService.deleteSession(agent, organizationId, SESSION_ID));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verifyNoInteractions(sessionQueryService, sessionRepository, eventPublisher);
    }

    @Test
    void deleteSession_personCaller_deletesTheSession() {
        User person = user(UserKind.HUMAN);
        Session session = Session.builder().organizationId(organizationId).userId(person.getId())
                .sessionId(SESSION_ID).build();
        session.setId(UUID.randomUUID());
        when(sessionQueryService.getEntityBySessionId(organizationId, SESSION_ID)).thenReturn(session);

        sessionService.deleteSession(person, organizationId, SESSION_ID);

        verify(userAccessHelper).requireAdminOrSelf(person, organizationId, person.getId());
        verify(sessionRepository).delete(session);
    }

    private static User user(UserKind kind) {
        return User.builder().id(UUID.randomUUID()).kind(kind).build();
    }
}
