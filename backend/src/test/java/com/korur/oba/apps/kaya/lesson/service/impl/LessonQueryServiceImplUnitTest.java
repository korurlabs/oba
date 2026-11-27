package com.korur.oba.apps.kaya.lesson.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party

// 3. Java SDK
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.dto.derived.LessonDelivery;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryProjection;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryStateProjection;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryResponse;
import com.korur.oba.apps.kaya.lesson.helper.LessonDeliveryRateLimiter;
import com.korur.oba.apps.kaya.lesson.helper.LessonEtagHelper;
import com.korur.oba.apps.kaya.lesson.mapper.LessonMapperImpl;
import com.korur.oba.apps.kaya.lesson.repository.LessonRepository;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;

import static com.korur.oba.apps.kaya.lesson.mock_data.LessonDeliveryMocks.row;
import static com.korur.oba.apps.kaya.lesson.mock_data.LessonDeliveryMocks.rows;
import static com.korur.oba.apps.kaya.lesson.mock_data.LessonDeliveryMocks.stateOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonQueryServiceImplUnitTest {

    private static final String REMOTE = "git@github.com:example/app.git";
    private static final int LESSONS = 123;
    // The time a refusing budget names until its window ends.
    private static final Duration WAIT = Duration.ofSeconds(17);

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
    private LessonDeliveryRateLimiter lessonDeliveryRateLimiter;
    @Mock
    private LoggingService loggingService;

    private final MachinePrincipal principal =
            new MachinePrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "laptop");
    private LessonQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LessonQueryServiceImpl(lessonRepository, sessionQueryService, kayaUserQueryService,
                repositoryQueryService, organizationAccessQueryService, lessonDeliveryRateLimiter, new LessonMapperImpl(),
                loggingService);
        // Every read is within the budgets unless a test says otherwise; a read refused before the
        // charge never asks.
        lenient().when(lessonDeliveryRateLimiter.tryChargeRead(principal.tokenId())).thenReturn(Optional.empty());
        lenient().when(lessonDeliveryRateLimiter.tryChargeUserRead(principal.userId())).thenReturn(Optional.empty());
        lenient().when(lessonDeliveryRateLimiter.tryChargeOrganizationRead(principal.organizationId())).thenReturn(Optional.empty());
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

    /**
     * Answers the state and the pages from lessons as they are at each call, so a test can change
     * them between two pages.
     */
    private void givenLessons(List<LessonDeliveryProjection> lessons) {
        when(repositoryQueryService.getRepositories(principal.organizationId())).thenReturn(List.of());
        when(lessonRepository.findDeliveryState(principal.organizationId(), null))
                .thenAnswer(invocation -> stateOf(lessons));
        lenient().when(lessonRepository.findDeliveryLessons(eq(principal.organizationId()), eq(null), any()))
                .thenAnswer(invocation -> {
                    Pageable page = invocation.getArgument(2);
                    return lessons.stream().skip(page.getOffset()).limit(page.getPageSize()).toList();
                });
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
            assertEquals("v2u-" + stateOf(rows(LESSONS)).getDigest(), delivery.etag());
        }

        @Test
        void withoutPaging_namesItsAnswerByTheUnpagedFormThatPage1NeverMatches() {
            givenLessons(rows(LESSONS));

            String unpaged = service.listForCheckout(principal, request(null, null, null)).etag();
            String paged = service.listForCheckout(principal, request(1, null, null)).etag();

            assertEquals(LessonEtagHelper.unpagedEtagOf(stateOf(rows(LESSONS))), unpaged);
            assertEquals(LessonEtagHelper.pagedEtagOf(stateOf(rows(LESSONS))), paged);
            assertNotEquals(unpaged, paged);
            assertFalse(LessonEtagHelper.matches("\"" + unpaged + "\"", paged));
            assertFalse(LessonEtagHelper.matches("\"" + paged + "\"", unpaged));
        }

        @Test
        void withoutPaging_unpagedFormChangesWithTheLessons() {
            List<LessonDeliveryProjection> lessons = rows(LESSONS);
            givenLessons(lessons);
            String before = service.listForCheckout(principal, request(null, null, null)).etag();

            lessons.remove(LESSONS - 1);

            assertNotEquals(before, service.listForCheckout(principal, request(null, null, null)).etag());
        }

        @Test
        void laterPageWithTheEtagOfPage1_returnsTheRestWithTheSameEtag() {
            givenLessons(rows(LESSONS));
            String etag = service.listForCheckout(principal, request(1, null, null)).etag();

            LessonDelivery second = service.listForCheckout(principal, request(2, null, "\"" + etag + "\""));

            assertEquals(23, second.lessons().getNumberOfElements());
            assertEquals("lesson 101", titles(second.lessons()).get(0));
            assertEquals("lesson 123", titles(second.lessons()).get(22));
            assertTrue(second.lessons().isLast());
            assertEquals(etag, second.etag());
            // The order lives in the query, so the page asked for carries none of its own.
            verify(lessonRepository).findDeliveryLessons(principal.organizationId(), null, PageRequest.of(1, 100));
        }

        @Test
        void laterPageWithTheWeakEtagOfPage1_returnsTheRestUntilTheLessonsChange() {
            // A proxy that compresses page 1 hands the CLI the ETag as W/"v2-...".
            List<LessonDeliveryProjection> lessons = rows(LESSONS);
            givenLessons(lessons);
            String weak = "W/\"" + service.listForCheckout(principal, request(1, null, null)).etag() + "\"";

            LessonDelivery second = service.listForCheckout(principal, request(2, null, weak));
            lessons.add(0, row(LESSONS + 1));

            assertEquals(23, second.lessons().getNumberOfElements());
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> service.listForCheckout(principal, request(2, null, weak)));
            assertEquals("KAY_VAL_017", exception.getErrorCode());
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
        void pageBeyondTheLast_isEmptyAndLastWithoutReadingAPage() {
            givenLessons(rows(3));
            String etag = LessonEtagHelper.pagedEtagOf(stateOf(rows(3)));

            Page<LessonDeliveryResponse> page = service.listForCheckout(principal, request(5, null, etag)).lessons();
            Page<LessonDeliveryResponse> farPage =
                    service.listForCheckout(principal, request(Integer.MAX_VALUE, null, etag)).lessons();

            assertTrue(page.isEmpty());
            assertTrue(page.isLast());
            assertEquals(3, page.getTotalElements());
            assertTrue(farPage.isEmpty());
            assertEquals(3, farPage.getTotalElements());
            verify(lessonRepository, never()).findDeliveryLessons(any(), any(), any());
        }

        @Test
        void lessonAddedAfterPage1_throwsLessonsChanged() {
            List<LessonDeliveryProjection> lessons = rows(LESSONS);
            givenLessons(lessons);
            String etag = service.listForCheckout(principal, request(1, null, null)).etag();
            lessons.add(0, row(LESSONS + 1));

            ConflictException exception = assertThrows(ConflictException.class,
                    () -> service.listForCheckout(principal, request(2, null, etag)));

            assertEquals("KAY_VAL_017", exception.getErrorCode());
        }

        @Test
        void lessonDeletedAfterPage1_throwsLessonsChanged() {
            List<LessonDeliveryProjection> lessons = rows(LESSONS);
            givenLessons(lessons);
            String etag = service.listForCheckout(principal, request(1, null, null)).etag();
            lessons.remove(LESSONS - 1);

            ConflictException exception = assertThrows(ConflictException.class,
                    () -> service.listForCheckout(principal, request(2, null, etag)));

            assertEquals("KAY_VAL_017", exception.getErrorCode());
        }

        @Test
        void read_chargesNothing_becauseItsAdmissionCharged() {
            givenLessons(rows(3));

            service.listForCheckout(principal, request(1, null, null));

            verifyNoInteractions(lessonDeliveryRateLimiter);
        }
    }

    /**
     * What a request must pass before it asks for a place among the reads that run at once. Nothing
     * here touches the database, so a refusal costs no permit and no pooled connection.
     */
    @Nested
    class Admission {

        @Test
        void admitListForCheckout_laterPageWithoutEtag_isRefusedWithoutACharge() {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> service.admitListForCheckout(principal, request(2, null, null)));

            assertEquals("GEN_VAL_001", exception.getErrorCode());
            assertTrue(exception.getErrorValues().containsKey("etag"));
            verifyNoInteractions(lessonDeliveryRateLimiter, lessonRepository, repositoryQueryService);
        }

        @Test
        void admitListForCheckout_unreadableSince_isRefusedWithoutACharge() {
            LessonDeliveryQueryRequest request = request(1, null, null);
            request.setSince("yesterday");

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> service.admitListForCheckout(principal, request));

            assertEquals("GEN_VAL_001", exception.getErrorCode());
            assertTrue(exception.getErrorValues().containsKey("since"));
            verifyNoInteractions(lessonDeliveryRateLimiter, lessonRepository, repositoryQueryService);
        }

        @Test
        void admitListForCheckout_laterPageWithEtag_isChargedWithoutTouchingTheDatabase() {
            service.admitListForCheckout(principal, request(2, null, "\"v2-" + "a".repeat(64) + "\""));

            verify(lessonDeliveryRateLimiter).tryChargeRead(principal.tokenId());
            verifyNoInteractions(lessonRepository, repositoryQueryService);
        }

        @Test
        void admissions_runOutsideAnyTransaction() throws NoSuchMethodException {
            // A transaction of its own would take a pooled connection at its start, refused or not.
            Transactional list = LessonQueryServiceImpl.class.getMethod("admitListForCheckout",
                    MachinePrincipal.class, LessonDeliveryQueryRequest.class).getAnnotation(Transactional.class);
            Transactional search = LessonQueryServiceImpl.class.getMethod("admitSearchForCheckout",
                    MachinePrincipal.class).getAnnotation(Transactional.class);

            assertEquals(Propagation.NOT_SUPPORTED, list.propagation());
            assertEquals(Propagation.NOT_SUPPORTED, search.propagation());
        }
    }

    @Nested
    class RateLimit {

        @Test
        void admitListForCheckout_withinTheBudget_chargesOneReadToTheTokenItsUserAndItsOrganization() {
            service.admitListForCheckout(principal, request(1, null, null));

            InOrder charges = inOrder(lessonDeliveryRateLimiter);
            charges.verify(lessonDeliveryRateLimiter).tryChargeRead(principal.tokenId());
            charges.verify(lessonDeliveryRateLimiter).tryChargeUserRead(principal.userId());
            charges.verify(lessonDeliveryRateLimiter).tryChargeOrganizationRead(principal.organizationId());
        }

        @Test
        void admitListForCheckout_userOverTheBudget_throwsTooManyRequestsWithItsLimit() {
            when(lessonDeliveryRateLimiter.tryChargeUserRead(principal.userId())).thenReturn(Optional.of(WAIT));

            TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                    () -> service.admitListForCheckout(principal, request(1, null, null)));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
            assertEquals("KAY_SEC_004", exception.getErrorCode());
            assertEquals(LessonConstant.DELIVERY_USER_READS_PER_MINUTE, exception.getErrorValues().get("limit"));
            assertEquals(LessonConstant.POLICY_USER_READS_PER_MINUTE, exception.getErrorValues().get(LessonConstant.FIELD_POLICY));
            assertEquals(WAIT, exception.getRetryAfter());
            verifyNoInteractions(lessonRepository, repositoryQueryService);
            // A read the person may not make costs their colleagues nothing.
            verify(lessonDeliveryRateLimiter, never()).tryChargeOrganizationRead(any());
        }

        @Test
        void admitSearchForCheckout_userOverTheBudget_throwsTooManyRequestsWithItsLimit() {
            when(lessonDeliveryRateLimiter.tryChargeUserRead(principal.userId())).thenReturn(Optional.of(WAIT));

            TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                    () -> service.admitSearchForCheckout(principal));

            assertEquals("KAY_SEC_004", exception.getErrorCode());
            assertEquals(LessonConstant.DELIVERY_USER_READS_PER_MINUTE, exception.getErrorValues().get("limit"));
            assertEquals(LessonConstant.POLICY_USER_READS_PER_MINUTE, exception.getErrorValues().get(LessonConstant.FIELD_POLICY));
            verifyNoInteractions(lessonRepository, repositoryQueryService);
            verify(lessonDeliveryRateLimiter, never()).tryChargeOrganizationRead(any());
        }

        @Test
        void admitListForCheckout_tokenOverTheBudget_throwsTooManyRequests() {
            when(lessonDeliveryRateLimiter.tryChargeRead(principal.tokenId())).thenReturn(Optional.of(WAIT));

            TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                    () -> service.admitListForCheckout(principal, request(1, null, null)));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
            assertEquals("KAY_SEC_004", exception.getErrorCode());
            assertEquals(LessonConstant.DELIVERY_READS_PER_MINUTE, exception.getErrorValues().get("limit"));
            assertEquals(LessonConstant.POLICY_TOKEN_READS_PER_MINUTE, exception.getErrorValues().get(LessonConstant.FIELD_POLICY));
            assertEquals(WAIT, exception.getRetryAfter());
            verifyNoInteractions(lessonRepository, repositoryQueryService);
            // A read the token may not make costs its colleagues nothing.
            verify(lessonDeliveryRateLimiter, never()).tryChargeUserRead(any());
            verify(lessonDeliveryRateLimiter, never()).tryChargeOrganizationRead(any());
        }

        @Test
        void admitListForCheckout_organizationOverTheBudget_throwsTooManyRequestsWithItsLimit() {
            when(lessonDeliveryRateLimiter.tryChargeOrganizationRead(principal.organizationId())).thenReturn(Optional.of(WAIT));

            TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                    () -> service.admitListForCheckout(principal, request(1, null, null)));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
            assertEquals("KAY_SEC_004", exception.getErrorCode());
            assertEquals(LessonConstant.DELIVERY_ORGANIZATION_READS_PER_MINUTE, exception.getErrorValues().get("limit"));
            assertEquals(LessonConstant.POLICY_ORGANIZATION_READS_PER_MINUTE, exception.getErrorValues().get(LessonConstant.FIELD_POLICY));
            assertEquals(WAIT, exception.getRetryAfter());
            verifyNoInteractions(lessonRepository, repositoryQueryService);
        }

        @Test
        void admitSearchForCheckout_organizationOverTheBudget_throwsTooManyRequestsWithItsLimit() {
            when(lessonDeliveryRateLimiter.tryChargeOrganizationRead(principal.organizationId())).thenReturn(Optional.of(WAIT));

            TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                    () -> service.admitSearchForCheckout(principal));

            assertEquals("KAY_SEC_004", exception.getErrorCode());
            assertEquals(LessonConstant.DELIVERY_ORGANIZATION_READS_PER_MINUTE, exception.getErrorValues().get("limit"));
            assertEquals(LessonConstant.POLICY_ORGANIZATION_READS_PER_MINUTE, exception.getErrorValues().get(LessonConstant.FIELD_POLICY));
            verifyNoInteractions(lessonRepository, repositoryQueryService);
        }

        @Test
        void admitSearchForCheckout_tokenOverTheBudget_throwsTooManyRequests() {
            when(lessonDeliveryRateLimiter.tryChargeRead(principal.tokenId())).thenReturn(Optional.of(WAIT));

            TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                    () -> service.admitSearchForCheckout(principal));

            assertEquals("KAY_SEC_004", exception.getErrorCode());
            assertEquals(LessonConstant.DELIVERY_READS_PER_MINUTE, exception.getErrorValues().get("limit"));
            assertEquals(LessonConstant.POLICY_TOKEN_READS_PER_MINUTE, exception.getErrorValues().get(LessonConstant.FIELD_POLICY));
            verifyNoInteractions(lessonRepository, repositoryQueryService);
        }
    }

    @Nested
    class EtagMatching {

        @Test
        void matches_quotedOrBare_compareTheValue() {
            String etag = LessonEtagHelper.pagedEtagOf(stateOf(rows(2)));

            assertTrue(LessonEtagHelper.matches(etag, etag));
            assertTrue(LessonEtagHelper.matches("\"" + etag + "\"", etag));
            assertFalse(LessonEtagHelper.matches("\"" + LessonEtagHelper.pagedEtagOf(stateOf(rows(3))) + "\"", etag));
        }

        @Test
        void matches_weak_comparesTheValueButKeepsTheForm() {
            LessonDeliveryStateProjection state = stateOf(rows(2));
            String paged = LessonEtagHelper.pagedEtagOf(state);

            assertTrue(LessonEtagHelper.matches("W/\"" + paged + "\"", paged));
            assertFalse(LessonEtagHelper.matches("W/\"" + LessonEtagHelper.pagedEtagOf(stateOf(rows(3))) + "\"", paged));
            // The same digest under the unpaged form still names another answer.
            assertFalse(LessonEtagHelper.matches("W/\"" + LessonEtagHelper.unpagedEtagOf(state) + "\"", paged));
        }

        @Test
        void pagedAndUnpagedEtagOf_carryTheirForms_soAnEtagOfTheEarlierFormMatchesNeither() {
            LessonDeliveryStateProjection state = stateOf(rows(2));
            String paged = LessonEtagHelper.pagedEtagOf(state);
            String unpaged = LessonEtagHelper.unpagedEtagOf(state);

            assertTrue(paged.matches("v2-[0-9a-f]{64}"), paged);
            assertTrue(unpaged.matches("v2u-[0-9a-f]{64}"), unpaged);
            // The earlier form was the bare 64 hex digest: even the same digest no longer matches.
            assertFalse(LessonEtagHelper.matches("\"" + state.getDigest() + "\"", paged));
            assertFalse(LessonEtagHelper.matches("\"" + state.getDigest() + "\"", unpaged));
        }
    }

    @Nested
    class ReadTimeLimit {

        @Test
        void listAndSearch_endAtTheReadTimeLimit() throws NoSuchMethodException {
            Transactional list = transactionOf(LessonQueryServiceImpl.class.getMethod("listForCheckout",
                    MachinePrincipal.class, LessonDeliveryQueryRequest.class));
            Transactional search = transactionOf(LessonQueryServiceImpl.class.getMethod("searchForCheckout",
                    MachinePrincipal.class, String.class, String.class));
            Transactional webList = transactionOf(LessonQueryServiceImpl.class.getMethod("listLessons",
                    User.class, UUID.class, LessonQueryRequest.class));

            assertEquals(LessonConstant.DELIVERY_READ_TIMEOUT_SECONDS, list.timeout());
            assertEquals(LessonConstant.DELIVERY_READ_TIMEOUT_SECONDS, search.timeout());
            assertEquals(LessonConstant.DELIVERY_READ_TIMEOUT_SECONDS, webList.timeout());
            // The list keeps the one snapshot that pairs a page with its ETag.
            assertEquals(Isolation.REPEATABLE_READ, list.isolation());
            assertTrue(list.readOnly() && search.readOnly() && webList.readOnly());
        }

        @Test
        void statementTimeout_isTheReadTimeLimitInPostgresUnits() {
            assertEquals(LessonConstant.DELIVERY_READ_TIMEOUT_SECONDS + "s", LessonConstant.DELIVERY_STATEMENT_TIMEOUT);
        }

        @Test
        void listForCheckout_limitsTheStatementTimeBeforeItsFirstQuery() {
            givenLessons(rows(3));

            service.listForCheckout(principal, request(1, null, null));

            InOrder statements = inOrder(lessonRepository, repositoryQueryService);
            statements.verify(lessonRepository).limitStatementTime(LessonConstant.DELIVERY_STATEMENT_TIMEOUT);
            statements.verify(repositoryQueryService).getRepositories(principal.organizationId());
            statements.verify(lessonRepository).findDeliveryState(principal.organizationId(), null);
            statements.verify(lessonRepository).findDeliveryLessons(eq(principal.organizationId()), eq(null), any());
        }

        @Test
        void searchForCheckout_limitsTheStatementTimeBeforeItsFirstQuery() {
            when(repositoryQueryService.getRepositories(principal.organizationId())).thenReturn(List.of());
            when(lessonRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

            service.searchForCheckout(principal, REMOTE, "deploy");

            InOrder statements = inOrder(lessonRepository, repositoryQueryService);
            statements.verify(lessonRepository).limitStatementTime(LessonConstant.DELIVERY_STATEMENT_TIMEOUT);
            statements.verify(repositoryQueryService).getRepositories(principal.organizationId());
            statements.verify(lessonRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        void listLessons_limitsTheStatementTimeBeforeItsFirstQueryAndPassesTheCancelOn() {
            // Arrange: PostgreSQL cancels the page at the statement limit (SQLSTATE 57014).
            QueryTimeoutException cancel = new QueryTimeoutException("canceling statement due to statement timeout");
            when(lessonRepository.findAll(any(Specification.class), any(Pageable.class))).thenThrow(cancel);
            User user = User.builder().id(UUID.randomUUID()).build();

            // Act: the cancel reaches the handler, which answers 503 GEN_UPS_004.
            QueryTimeoutException thrown = assertThrows(QueryTimeoutException.class,
                    () -> service.listLessons(user, principal.organizationId(), new LessonQueryRequest()));

            // Assert
            assertEquals(cancel, thrown);
            InOrder statements = inOrder(lessonRepository);
            statements.verify(lessonRepository).limitStatementTime(LessonConstant.DELIVERY_STATEMENT_TIMEOUT);
            statements.verify(lessonRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        void getFilters_endsAtTheReadTimeLimit() throws NoSuchMethodException {
            Transactional filters = transactionOf(LessonQueryServiceImpl.class.getMethod("getFilters", UUID.class));

            assertEquals(LessonConstant.DELIVERY_READ_TIMEOUT_SECONDS, filters.timeout());
            assertTrue(filters.readOnly());
        }

        @Test
        void getFilters_limitsTheStatementTimeBeforeItsFirstQueryAndPassesTheCancelOn() {
            QueryTimeoutException cancel = new QueryTimeoutException("canceling statement due to statement timeout");
            when(lessonRepository.findDistinctUserIds(principal.organizationId())).thenThrow(cancel);

            QueryTimeoutException thrown = assertThrows(QueryTimeoutException.class,
                    () -> service.getFilters(principal.organizationId()));

            assertEquals(cancel, thrown);
            InOrder statements = inOrder(lessonRepository);
            statements.verify(lessonRepository).limitStatementTime(LessonConstant.DELIVERY_STATEMENT_TIMEOUT);
            statements.verify(lessonRepository).findDistinctUserIds(principal.organizationId());
        }

        private static Transactional transactionOf(Method method) {
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertTrue(transactional != null, method.getName() + " has a transaction of its own");
            return transactional;
        }
    }
}
