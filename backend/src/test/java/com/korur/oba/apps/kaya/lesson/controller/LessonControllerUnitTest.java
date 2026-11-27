package com.korur.oba.apps.kaya.lesson.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

// 3. Java SDK
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.exception.internal.infrastructure.ServiceUnavailableException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDetailResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonFiltersResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonListResponse;
import com.korur.oba.apps.kaya.lesson.helper.LessonDeliveryBulkhead;
import com.korur.oba.apps.kaya.lesson.service.LessonQueryService;
import com.korur.oba.apps.kaya.lesson.service.LessonService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The web list and its filter options run within the caps on lesson reads at once, which they share
 * with the reads of the CLI, and a lesson added in the web app is charged before it is written.
 */
@ExtendWith(MockitoExtension.class)
class LessonControllerUnitTest {

    @Mock
    private LessonService lessonService;
    @Mock
    private LessonQueryService lessonQueryService;
    @Mock
    private LoggingService loggingService;

    private final UUID organizationId = UUID.randomUUID();
    private final User user = User.builder().id(UUID.randomUUID()).build();
    private LessonDeliveryBulkhead bulkhead;
    private LessonController controller;

    @BeforeEach
    void setUp() {
        bulkhead = new LessonDeliveryBulkhead(loggingService);
        controller = new LessonController(lessonService, lessonQueryService, bulkhead, loggingService);
    }

    /** Runs the request while the given machines each hold a read, the last one innermost. */
    private <T> T whileReading(List<MachinePrincipal> machines, Supplier<T> request) {
        if (machines.isEmpty()) {
            return request.get();
        }
        return bulkhead.run(machines.getFirst(), () -> whileReading(machines.subList(1, machines.size()), request));
    }

    private static MachinePrincipal machineOf(UUID organizationId, UUID userId) {
        return new MachinePrincipal(organizationId, userId, UUID.randomUUID(), UUID.randomUUID(), "laptop");
    }

    /** The reads of other organizations that fill the instance. */
    private static List<MachinePrincipal> aFullInstance() {
        return IntStream.range(0, LessonConstant.DELIVERY_CONCURRENT_READS_MAX)
                .mapToObj(read -> machineOf(UUID.randomUUID(), UUID.randomUUID()))
                .toList();
    }

    /** The CLI reads of the same member, in organizations of their own, that fill the member's cap. */
    private List<MachinePrincipal> theMemberAtTheirCap() {
        return IntStream.range(0, LessonConstant.DELIVERY_USER_CONCURRENT_READS_MAX)
                .mapToObj(read -> machineOf(UUID.randomUUID(), user.getId()))
                .toList();
    }

    /** The CLI reads of colleagues in the same organization that fill the organization's cap. */
    private List<MachinePrincipal> theOrganizationAtItsCap() {
        return IntStream.range(0, LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX)
                .mapToObj(read -> machineOf(organizationId, UUID.randomUUID()))
                .toList();
    }

    @Nested
    class WebList {

        @Test
        void getLessons_withAPlaceFree_readsAndGivesThePlaceBack() {
            LessonQueryRequest request = new LessonQueryRequest();
            when(lessonQueryService.listLessons(user, organizationId, request)).thenReturn(Page.empty());

            assertEquals(200, controller.getLessons(user, organizationId, request).getStatusCode().value());

            // Every place is free again: a full instance and the member's own cap still fit.
            whileReading(aFullInstance(), () -> null);
            whileReading(theMemberAtTheirCap(), () -> null);
        }

        @Test
        void getLessons_onAFullInstance_refuses503BeforeAnyRead() {
            ServiceUnavailableException refusal = assertThrows(ServiceUnavailableException.class,
                    () -> whileReading(aFullInstance(), () -> controller.getLessons(user, organizationId, new LessonQueryRequest())));

            assertEquals(LessonConstant.DELIVERY_BUSY_RETRY_AFTER, refusal.getRetryAfter());
            verifyNoInteractions(lessonQueryService);
        }

        @Test
        void getLessons_memberAtTheirCapWithTheCli_refuses429NamingTheMember() {
            TooManyRequestsException refusal = assertThrows(TooManyRequestsException.class,
                    () -> whileReading(theMemberAtTheirCap(),
                            () -> controller.getLessons(user, organizationId, new LessonQueryRequest())));

            assertEquals(ExceptionDomainKayaError.SEC_RATE_LIMITED.getCode(), refusal.getErrorCode());
            assertEquals(LessonConstant.POLICY_USER_READS_AT_ONCE, refusal.getErrorValues().get(LessonConstant.FIELD_POLICY));
            verifyNoInteractions(lessonQueryService);
        }

        @Test
        void getLessons_failedRead_givesThePlaceBack() {
            LessonQueryRequest request = new LessonQueryRequest();
            IllegalStateException failure = new IllegalStateException("read failed");
            when(lessonQueryService.listLessons(user, organizationId, request)).thenThrow(failure);

            for (int read = 0; read <= LessonConstant.DELIVERY_USER_CONCURRENT_READS_MAX; read++) {
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> controller.getLessons(user, organizationId, request)));
            }

            whileReading(theMemberAtTheirCap(), () -> null);
        }
    }

    @Nested
    class Filters {

        @Test
        void getFilters_withAPlaceFree_reads() {
            LessonFiltersResponse filters = new LessonFiltersResponse(List.of(), List.of());
            when(lessonQueryService.getFilters(organizationId)).thenReturn(filters);

            assertSame(filters, controller.getFilters(user, organizationId).getBody().data());
        }

        @Test
        void getFilters_onAFullInstance_refuses503BeforeAnyRead() {
            assertThrows(ServiceUnavailableException.class,
                    () -> whileReading(aFullInstance(), () -> controller.getFilters(user, organizationId)));

            verifyNoInteractions(lessonQueryService);
        }

        @Test
        void getFilters_organizationAtItsCapWithTheCli_refuses429NamingTheOrganization() {
            TooManyRequestsException refusal = assertThrows(TooManyRequestsException.class,
                    () -> whileReading(theOrganizationAtItsCap(), () -> controller.getFilters(user, organizationId)));

            assertEquals(LessonConstant.POLICY_ORGANIZATION_READS_AT_ONCE,
                    refusal.getErrorValues().get(LessonConstant.FIELD_POLICY));
            verifyNoInteractions(lessonQueryService);
        }

        @Test
        void listAndFiltersOfOnePageLoad_runAtOnceWithinTheMembersCap() {
            LessonQueryRequest request = new LessonQueryRequest();
            when(lessonQueryService.listLessons(user, organizationId, request)).thenReturn(Page.empty());
            // The filters answer while the list of the same page load still holds its place.
            when(lessonQueryService.getFilters(organizationId)).thenAnswer(invocation -> {
                controller.getLessons(user, organizationId, request);
                return new LessonFiltersResponse(List.of(), List.of());
            });

            assertEquals(200, controller.getFilters(user, organizationId).getStatusCode().value());
        }
    }

    @Nested
    class Create {

        private final LessonRequest request = new LessonRequest("Run the migration first", "The entity landed first.", null);

        @Test
        void createLesson_chargesTheMemberBeforeTheLessonIsWritten() {
            LessonDetailResponse created = new LessonDetailResponse(UUID.randomUUID(), request.title(), request.body(),
                    null, null, null, Instant.now(), Instant.now(), true, null);
            when(lessonService.create(user, organizationId, request)).thenReturn(created);

            assertEquals(201, controller.createLesson(user, organizationId, request).getStatusCode().value());

            InOrder order = inOrder(lessonService);
            order.verify(lessonService).admitCreate(user);
            order.verify(lessonService).create(user, organizationId, request);
        }

        @Test
        void createLesson_memberOverTheBudget_writesNothing() {
            TooManyRequestsException refusal = new TooManyRequestsException(ExceptionDomainKayaError.SEC_RATE_LIMITED,
                    Map.of(LessonConstant.FIELD_LIMIT, LessonConstant.USER_LESSONS_PER_MINUTE), Duration.ofSeconds(30));
            doThrow(refusal).when(lessonService).admitCreate(user);

            assertSame(refusal, assertThrows(TooManyRequestsException.class,
                    () -> controller.createLesson(user, organizationId, request)));

            verify(lessonService).admitCreate(user);
            verify(lessonService, never()).create(any(User.class), any(UUID.class), any(LessonRequest.class));
        }
    }
}
