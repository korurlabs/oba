package com.korur.oba.apps.kaya.lesson.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.delivery.DeliveryProposalProperties;
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonRequest;
import com.korur.oba.apps.kaya.lesson.entity.Lesson;
import com.korur.oba.apps.kaya.lesson.mapper.LessonMapper;
import com.korur.oba.apps.kaya.lesson.repository.LessonRepository;
import com.korur.oba.apps.kaya.lesson.service.LessonQueryService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Runs the lesson changes through the real {@link AgentValidator}: lessons reach every later session
 * with no approval step, so an agent never writes, rewrites or removes one, whether it calls the web
 * API or its machine token.
 */
@ExtendWith(MockitoExtension.class)
class LessonServiceImplUnitAgentTest {

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonQueryService lessonQueryService;
    @Mock
    private UserAccessHelper userAccessHelper;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private LessonMapper lessonMapper;

    private LessonServiceImpl lessonService;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        lessonService = new LessonServiceImpl(lessonRepository, lessonQueryService, mock(RepositoryQueryService.class),
                userAccessHelper, new AgentValidator(userQueryService, loggingService), lessonMapper,
                mock(DeliveryProposalProperties.class), loggingService);
    }

    @Test
    void create_agentCaller_throwsForbiddenAndSavesNothing() {
        User agent = user(UserKind.AGENT);

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> lessonService.create(
                agent, organizationId, new LessonRequest("Title", "Body", null), null));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        assertEquals("KAYA_LESSON_CREATE", exception.getErrorValues().get("action"));
        verifyNoInteractions(lessonRepository, lessonMapper);
    }

    @Test
    void create_agentThroughItsMachineTokenForTheWholeOrganization_throwsForbiddenAndSavesNothing() {
        User agent = user(UserKind.AGENT);
        MachinePrincipal principal = new MachinePrincipal(organizationId, agent.getId(), UUID.randomUUID(),
                UUID.randomUUID(), "cloud");
        LessonDeliveryRequest request = new LessonDeliveryRequest("git@github.com:example/repo.git", "Title", "Body",
                true, null);
        when(lessonQueryService.getActingUser(principal)).thenReturn(agent);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> lessonService.create(principal, request));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verifyNoInteractions(lessonRepository, lessonMapper);
    }

    @Test
    void create_agentThroughItsMachineTokenForAnUnknownCheckout_throwsForbiddenBeforeTheCheckoutLookup() {
        User agent = user(UserKind.AGENT);
        MachinePrincipal principal = new MachinePrincipal(organizationId, agent.getId(), UUID.randomUUID(),
                UUID.randomUUID(), "cloud");
        LessonDeliveryRequest request = new LessonDeliveryRequest("git@github.com:example/unknown.git", "Title",
                "Body", false, null);
        when(lessonQueryService.getActingUser(principal)).thenReturn(agent);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> lessonService.create(principal, request));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        assertEquals("KAYA_LESSON_CREATE", exception.getErrorValues().get("action"));
        verify(lessonQueryService, never()).findCheckoutRepository(any(), any());
        verifyNoInteractions(lessonRepository, lessonMapper);
    }

    @Test
    void create_personCaller_savesTheLesson() {
        User person = user(UserKind.HUMAN);
        LessonRequest request = new LessonRequest("Title", "Body", null);
        Lesson lesson = lessonOf(person);
        when(lessonMapper.toEntity(request, organizationId, person.getId(), null)).thenReturn(lesson);
        when(lessonRepository.save(lesson)).thenReturn(lesson);

        lessonService.create(person, organizationId, request, null);

        verify(lessonRepository).save(lesson);
        verify(lessonQueryService).toDetailResponse(person, organizationId, lesson);
    }

    @Test
    void update_agentCaller_throwsForbiddenAndChangesNothing() {
        User agent = user(UserKind.AGENT);

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> lessonService.update(
                agent, organizationId, UUID.randomUUID(), new LessonRequest("Title", "Body", null)));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verifyNoInteractions(lessonQueryService, lessonRepository, userAccessHelper);
    }

    @Test
    void update_personCaller_savesTheLesson() {
        User person = user(UserKind.HUMAN);
        Lesson lesson = lessonOf(person);
        when(lessonQueryService.getEntity(organizationId, lesson.getId())).thenReturn(lesson);
        when(lessonRepository.saveAndFlush(lesson)).thenReturn(lesson);

        lessonService.update(person, organizationId, lesson.getId(), new LessonRequest("Title", "Body", null));

        verify(userAccessHelper).requireAdminOrSelf(person, organizationId, person.getId());
        verify(lessonRepository).saveAndFlush(lesson);
    }

    @Test
    void delete_agentCaller_throwsForbiddenAndDeletesNothing() {
        User agent = user(UserKind.AGENT);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> lessonService.delete(agent, organizationId, UUID.randomUUID()));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verifyNoInteractions(lessonRepository, userAccessHelper);
    }

    @Test
    void delete_agentThroughItsMachineToken_throwsForbiddenAndDeletesNothing() {
        User agent = user(UserKind.AGENT);
        MachinePrincipal principal = new MachinePrincipal(organizationId, agent.getId(), UUID.randomUUID(),
                UUID.randomUUID(), "cloud");
        when(lessonQueryService.getActingUser(principal)).thenReturn(agent);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> lessonService.delete(principal, UUID.randomUUID()));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verify(lessonRepository, never()).delete(any(Lesson.class));
    }

    @Test
    void delete_personCaller_deletesTheLesson() {
        User person = user(UserKind.HUMAN);
        Lesson lesson = lessonOf(person);
        when(lessonQueryService.getEntity(organizationId, lesson.getId())).thenReturn(lesson);

        lessonService.delete(person, organizationId, lesson.getId());

        verify(lessonRepository).delete(lesson);
    }

    private Lesson lessonOf(User owner) {
        Lesson lesson = Lesson.builder().organizationId(organizationId).userId(owner.getId()).build();
        lesson.setId(UUID.randomUUID());
        return lesson;
    }

    private static User user(UserKind kind) {
        return User.builder().id(UUID.randomUUID()).kind(kind).build();
    }
}
