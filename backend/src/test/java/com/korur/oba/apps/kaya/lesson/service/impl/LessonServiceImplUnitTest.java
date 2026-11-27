package com.korur.oba.apps.kaya.lesson.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 3. Java SDK
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.delivery.DeliveryProposalProperties;
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryCreatedResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDetailResponse;
import com.korur.oba.apps.kaya.lesson.entity.Lesson;
import com.korur.oba.apps.kaya.lesson.helper.LessonDeliveryRateLimiter;
import com.korur.oba.apps.kaya.lesson.mapper.LessonMapperImpl;
import com.korur.oba.apps.kaya.lesson.repository.LessonRepository;
import com.korur.oba.apps.kaya.lesson.service.LessonQueryService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonServiceImplUnitTest {

    private static final String REMOTE = "git@github.com:example/app.git";
    private static final String WEB_URL = "https://app.example.com";

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonQueryService lessonQueryService;
    @Mock
    private RepositoryQueryService repositoryQueryService;
    @Mock
    private UserAccessHelper userAccessHelper;
    @Mock
    private LessonDeliveryRateLimiter lessonDeliveryRateLimiter;
    @Mock
    private LoggingService loggingService;

    private final MachinePrincipal principal =
            new MachinePrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "laptop");
    private final LessonDeliveryRequest repoWideLesson =
            new LessonDeliveryRequest(REMOTE, "Run the migration first", "The entity landed before its table.", true, null);
    private LessonServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LessonServiceImpl(lessonRepository, lessonQueryService, repositoryQueryService, userAccessHelper,
                new LessonMapperImpl(), new DeliveryProposalProperties(WEB_URL), lessonDeliveryRateLimiter, loggingService);
    }

    @Nested
    class ProposalRateLimit {

        @Test
        void createFromCheckout_tokenOverTheBudget_throwsTooManyRequestsBeforeAnyReadOrWrite() {
            // Arrange
            when(lessonDeliveryRateLimiter.tryChargeProposal(principal.tokenId())).thenReturn(Optional.of(Duration.ofSeconds(42)));

            // Act
            TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                    () -> service.create(principal, repoWideLesson));

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
            assertEquals("KAY_SEC_004", exception.getErrorCode());
            assertEquals(LessonConstant.DELIVERY_PROPOSALS_PER_MINUTE, exception.getErrorValues().get(LessonConstant.FIELD_LIMIT));
            assertEquals(Duration.ofSeconds(42), exception.getRetryAfter());
            verifyNoInteractions(lessonQueryService, repositoryQueryService, lessonRepository);
        }

        @Test
        void createFromCheckout_withinTheBudget_chargesTheTokenOnceAndThenAddsTheLesson() {
            // Arrange
            User user = User.builder().build();
            user.setId(principal.userId());
            UUID lessonId = UUID.randomUUID();
            when(lessonDeliveryRateLimiter.tryChargeProposal(principal.tokenId())).thenReturn(Optional.empty());
            when(lessonQueryService.getActingUser(principal)).thenReturn(user);
            when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
                Lesson lesson = invocation.getArgument(0);
                lesson.setId(lessonId);
                return lesson;
            });
            when(lessonQueryService.toDetailResponse(eq(user), eq(principal.organizationId()), any(Lesson.class)))
                    .thenReturn(new LessonDetailResponse(lessonId, repoWideLesson.title(), repoWideLesson.body(), null, null,
                            null, Instant.now(), Instant.now(), true, null));

            // Act
            LessonDeliveryCreatedResponse created = service.create(principal, repoWideLesson);

            // Assert
            assertEquals(lessonId, created.id());
            InOrder order = inOrder(lessonDeliveryRateLimiter, lessonQueryService, lessonRepository);
            order.verify(lessonDeliveryRateLimiter).tryChargeProposal(principal.tokenId());
            order.verify(lessonQueryService).getActingUser(principal);
            order.verify(lessonRepository).save(any(Lesson.class));
        }
    }

    @Nested
    class WebCreateRateLimit {

        private final User user = User.builder().id(UUID.randomUUID()).build();

        @Test
        void admitCreate_memberOverTheBudget_throwsTooManyRequestsWithTheWaitAndTouchesNothing() {
            // Arrange
            when(lessonDeliveryRateLimiter.tryChargeUserLesson(user.getId())).thenReturn(Optional.of(Duration.ofSeconds(25)));

            // Act
            TooManyRequestsException exception = assertThrows(TooManyRequestsException.class, () -> service.admitCreate(user));

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
            assertEquals("KAY_SEC_004", exception.getErrorCode());
            assertEquals(LessonConstant.USER_LESSONS_PER_MINUTE, exception.getErrorValues().get(LessonConstant.FIELD_LIMIT));
            assertEquals(LessonConstant.POLICY_USER_LESSONS_PER_MINUTE,
                    exception.getErrorValues().get(LessonConstant.FIELD_POLICY));
            assertEquals(Duration.ofSeconds(25), exception.getRetryAfter());
            verifyNoInteractions(lessonQueryService, repositoryQueryService, lessonRepository);
        }

        @Test
        void admitCreate_withinTheBudget_chargesTheMemberOnly() {
            when(lessonDeliveryRateLimiter.tryChargeUserLesson(user.getId())).thenReturn(Optional.empty());

            service.admitCreate(user);

            verify(lessonDeliveryRateLimiter).tryChargeUserLesson(user.getId());
            verifyNoMoreInteractions(lessonDeliveryRateLimiter);
            verifyNoInteractions(lessonQueryService, repositoryQueryService, lessonRepository);
        }

        @Test
        void admitCreate_runsOutsideAnyTransaction() throws NoSuchMethodException {
            // A refused lesson must not take a connection of the pool, which a transaction takes when it begins.
            Transactional transactional = LessonServiceImpl.class.getMethod("admitCreate", User.class)
                    .getAnnotation(Transactional.class);

            assertEquals(Propagation.NOT_SUPPORTED, transactional.propagation());
        }

        @Test
        void createFromCheckout_chargesTheTokenNotTheMembersWebBudget() {
            when(lessonDeliveryRateLimiter.tryChargeProposal(principal.tokenId())).thenReturn(Optional.of(Duration.ofSeconds(1)));

            assertThrows(TooManyRequestsException.class, () -> service.create(principal, repoWideLesson));

            verify(lessonDeliveryRateLimiter, never()).tryChargeUserLesson(any());
        }
    }
}
