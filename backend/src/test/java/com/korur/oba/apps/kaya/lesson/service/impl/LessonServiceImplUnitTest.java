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

// 3. Java SDK
import java.time.Instant;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
            when(lessonDeliveryRateLimiter.tryChargeProposal(principal.tokenId())).thenReturn(false);

            // Act
            TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                    () -> service.create(principal, repoWideLesson));

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
            assertEquals("KAY_SEC_004", exception.getErrorCode());
            assertEquals(LessonConstant.DELIVERY_PROPOSALS_PER_MINUTE, exception.getErrorValues().get(LessonConstant.FIELD_LIMIT));
            verifyNoInteractions(lessonQueryService, repositoryQueryService, lessonRepository);
        }

        @Test
        void createFromCheckout_withinTheBudget_chargesTheTokenOnceAndThenAddsTheLesson() {
            // Arrange
            User user = User.builder().build();
            user.setId(principal.userId());
            UUID lessonId = UUID.randomUUID();
            when(lessonDeliveryRateLimiter.tryChargeProposal(principal.tokenId())).thenReturn(true);
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
}
