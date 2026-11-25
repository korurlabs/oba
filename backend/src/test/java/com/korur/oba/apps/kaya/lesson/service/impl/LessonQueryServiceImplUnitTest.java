package com.korur.oba.apps.kaya.lesson.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.apps.kaya.lesson.dto.derived.LessonDelivery;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryProjection;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryResponse;
import com.korur.oba.apps.kaya.lesson.helper.LessonEtagHelper;
import com.korur.oba.apps.kaya.lesson.mapper.LessonMapperImpl;
import com.korur.oba.apps.kaya.lesson.repository.LessonRepository;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonQueryServiceImplUnitTest {

    private static final String REMOTE = "git@github.com:example/app.git";
    private static final int LESSONS = 123;

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private SessionQueryService sessionQueryService;
    @Mock
    private KayaUserQueryService kayaUserQueryService;
    @Mock
    private RepositoryQueryService repositoryQueryService;
    @Mock
    private OrganizationAccessQueryService organizationAccessQueryService;
    @Mock
    private LoggingService loggingService;

    private final MachinePrincipal principal =
            new MachinePrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "laptop");
    private LessonQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LessonQueryServiceImpl(lessonRepository, sessionQueryService, kayaUserQueryService,
                repositoryQueryService, organizationAccessQueryService, new LessonMapperImpl(), loggingService);
    }

    /** A lesson of the organization, numbered newest first as the repository returns them. */
    private static LessonDeliveryProjection row(int number) {
        UUID id = new UUID(0L, number);
        Instant updatedAt = Instant.parse("2026-10-01T00:00:00Z").minusSeconds(number);
        return new LessonDeliveryProjection() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getTitle() {
                return "lesson " + number;
            }

            @Override
            public UUID getRepositoryId() {
                return null;
            }

            @Override
            public Instant getUpdatedAt() {
                return updatedAt;
            }
        };
    }

    private static List<LessonDeliveryProjection> rows(int count) {
        return new ArrayList<>(IntStream.rangeClosed(1, count).mapToObj(LessonQueryServiceImplUnitTest::row).toList());
    }

    private static LessonDeliveryQueryRequest request(Integer page, Integer amount, String etag) {
        LessonDeliveryQueryRequest request = new LessonDeliveryQueryRequest();
        request.setRemoteUrl(REMOTE);
        if (page != null) {
            request.setPage(page);
        }
        if (amount != null) {
            request.setAmount(amount);
        }
        request.setEtag(etag);
        return request;
    }

    private void givenLessons(List<LessonDeliveryProjection> lessons) {
        when(repositoryQueryService.getRepositories(principal.organizationId())).thenReturn(List.of());
        when(lessonRepository.findDeliveryLessons(principal.organizationId(), null)).thenReturn(lessons);
    }

    private static List<String> titles(Page<LessonDeliveryResponse> page) {
        return page.getContent().stream().map(LessonDeliveryResponse::title).toList();
    }

    @Nested
    class ListForCheckout {

        @Test
        void withoutPaging_returnsTheNewest100AsPage1Of2() {
            givenLessons(rows(LESSONS));

            LessonDelivery delivery = service.listForCheckout(principal, request(null, null, null));

            Page<LessonDeliveryResponse> page = delivery.lessons();
            assertEquals(100, page.getNumberOfElements());
            assertEquals("lesson 1", titles(page).get(0));
            assertEquals("lesson 100", titles(page).get(99));
            assertEquals(LESSONS, page.getTotalElements());
            assertEquals(2, page.getTotalPages());
            assertFalse(page.isLast());
            assertEquals(LessonEtagHelper.etagOf(rows(LESSONS)), delivery.etag());
        }

        @Test
        void laterPageWithTheEtagOfPage1_returnsTheRestWithTheSameEtag() {
            List<LessonDeliveryProjection> lessons = rows(LESSONS);
            givenLessons(lessons);
            String etag = service.listForCheckout(principal, request(1, null, null)).etag();

            LessonDelivery second = service.listForCheckout(principal, request(2, null, "\"" + etag + "\""));

            assertEquals(23, second.lessons().getNumberOfElements());
            assertEquals("lesson 101", titles(second.lessons()).get(0));
            assertEquals("lesson 123", titles(second.lessons()).get(22));
            assertTrue(second.lessons().isLast());
            assertEquals(etag, second.etag());
        }

        @Test
        void pagesOfAnyAmount_togetherHoldEveryLessonOnceInOrder() {
            givenLessons(rows(LESSONS));
            String etag = service.listForCheckout(principal, request(1, 10, null)).etag();

            List<String> delivered = new ArrayList<>();
            for (int number = 1; number <= 13; number++) {
                delivered.addAll(titles(service.listForCheckout(principal, request(number, 10, etag)).lessons()));
            }

            assertEquals(IntStream.rangeClosed(1, LESSONS).mapToObj(number -> "lesson " + number).toList(), delivered);
        }

        @Test
        void pageBeyondTheLast_isEmptyAndLast() {
            givenLessons(rows(3));
            String etag = LessonEtagHelper.etagOf(rows(3));

            Page<LessonDeliveryResponse> page = service.listForCheckout(principal, request(5, null, etag)).lessons();

            assertTrue(page.isEmpty());
            assertTrue(page.isLast());
            assertEquals(3, page.getTotalElements());
        }

        @Test
        void lessonsChangedAfterPage1_throwsLessonsChanged() {
            List<LessonDeliveryProjection> lessons = rows(LESSONS);
            givenLessons(lessons);
            String etag = service.listForCheckout(principal, request(1, null, null)).etag();
            lessons.add(0, row(LESSONS + 1));

            ConflictException exception = assertThrows(ConflictException.class,
                    () -> service.listForCheckout(principal, request(2, null, etag)));

            assertEquals("KAY_VAL_017", exception.getErrorCode());
        }

        @Test
        void laterPageWithoutEtag_isRefusedBeforeAnyRead() {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> service.listForCheckout(principal, request(2, null, null)));

            assertEquals("GEN_VAL_001", exception.getErrorCode());
            assertTrue(exception.getErrorValues().containsKey("etag"));
            verify(lessonRepository, never()).findDeliveryLessons(any(), any());
        }
    }

    @Nested
    class EtagMatching {

        @Test
        void matches_quotedOrBare_compareTheValue() {
            String etag = LessonEtagHelper.etagOf(rows(2));

            assertTrue(LessonEtagHelper.matches(etag, etag));
            assertTrue(LessonEtagHelper.matches("\"" + etag + "\"", etag));
            assertFalse(LessonEtagHelper.matches("\"" + LessonEtagHelper.etagOf(rows(3)) + "\"", etag));
        }
    }
}
