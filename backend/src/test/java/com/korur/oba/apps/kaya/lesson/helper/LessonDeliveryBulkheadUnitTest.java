package com.korur.oba.apps.kaya.lesson.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionUpstreamError;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.exception.internal.infrastructure.ServiceUnavailableException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The caps on lesson reads at the same time. A read that is still running is a call of run inside the
 * read of another, so the tests need no threads: every outer read holds its permits while the inner
 * one asks for its own.
 */
@ExtendWith(MockitoExtension.class)
class LessonDeliveryBulkheadUnitTest {

    private static final String ANSWER = "answer";

    @Mock
    private LoggingService loggingService;

    private LessonDeliveryBulkhead bulkhead() {
        return new LessonDeliveryBulkhead(loggingService);
    }

    private static MachinePrincipal machineOf(UUID organizationId) {
        return new MachinePrincipal(organizationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "laptop");
    }

    /**
     * Runs the reads of the given machines inside one another, the last one innermost, and returns
     * what the innermost read returns.
     */
    private static <T> T runNested(LessonDeliveryBulkhead bulkhead, List<MachinePrincipal> machines, Supplier<T> innermost) {
        if (machines.isEmpty()) {
            return innermost.get();
        }
        return bulkhead.run(machines.getFirst(), () -> runNested(bulkhead, machines.subList(1, machines.size()), innermost));
    }

    /** The machines of as many organizations as the instance runs reads, each with one read. */
    private static List<MachinePrincipal> fillTheInstance() {
        List<MachinePrincipal> machines = new ArrayList<>();
        for (int read = 0; read < LessonConstant.DELIVERY_CONCURRENT_READS_MAX; read++) {
            machines.add(machineOf(UUID.randomUUID()));
        }
        return machines;
    }

    /** The machines of one organization, as many as the organization may run reads. */
    private static List<MachinePrincipal> fillTheOrganization(UUID organizationId) {
        List<MachinePrincipal> machines = new ArrayList<>();
        for (int read = 0; read < LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX; read++) {
            machines.add(machineOf(organizationId));
        }
        return machines;
    }

    @Nested
    class OrganizationCap {

        @Test
        void run_atTheCapOfTheOrganization_runsEveryRead() {
            LessonDeliveryBulkhead bulkhead = bulkhead();

            assertEquals(ANSWER, runNested(bulkhead, fillTheOrganization(UUID.randomUUID()), () -> ANSWER));
        }

        @Test
        void run_overTheCapOfTheOrganization_refuses429WithTheLimit() {
            LessonDeliveryBulkhead bulkhead = bulkhead();
            UUID organizationId = UUID.randomUUID();

            TooManyRequestsException refusal = assertThrows(TooManyRequestsException.class,
                    () -> runNested(bulkhead, fillTheOrganization(organizationId),
                            () -> bulkhead.run(machineOf(organizationId), () -> ANSWER)));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, refusal.getStatusCode());
            assertEquals(ExceptionDomainKayaError.SEC_RATE_LIMITED.getCode(), refusal.getErrorCode());
            assertEquals(LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX,
                    refusal.getErrorValues().get(LessonConstant.FIELD_LIMIT));
        }

        @Test
        void run_organizationAtItsCap_leavesAnotherOrganizationAlone() {
            LessonDeliveryBulkhead bulkhead = bulkhead();

            String answer = runNested(bulkhead, fillTheOrganization(UUID.randomUUID()),
                    () -> bulkhead.run(machineOf(UUID.randomUUID()), () -> ANSWER));

            assertEquals(ANSWER, answer);
        }

        @Test
        void run_organizationOverItsCapOnAFullInstance_isToldItIsTheCause() {
            LessonDeliveryBulkhead bulkhead = bulkhead();
            UUID organizationId = UUID.randomUUID();
            List<MachinePrincipal> machines = new ArrayList<>(fillTheOrganization(organizationId));
            while (machines.size() < LessonConstant.DELIVERY_CONCURRENT_READS_MAX) {
                machines.add(machineOf(UUID.randomUUID()));
            }

            // The organization is asked before the instance, so its surplus read hears 429, which
            // names it, rather than the 503 of a busy server.
            assertThrows(TooManyRequestsException.class, () -> runNested(bulkhead, machines,
                    () -> bulkhead.run(machineOf(organizationId), () -> ANSWER)));
        }
    }

    @Nested
    class InstanceCap {

        @Test
        void run_atTheCapOfTheInstance_runsEveryRead() {
            assertEquals(ANSWER, runNested(bulkhead(), fillTheInstance(), () -> ANSWER));
        }

        @Test
        void run_overTheCapOfTheInstance_refuses503ServerBusy() {
            LessonDeliveryBulkhead bulkhead = bulkhead();

            ServiceUnavailableException refusal = assertThrows(ServiceUnavailableException.class,
                    () -> runNested(bulkhead, fillTheInstance(), () -> bulkhead.run(machineOf(UUID.randomUUID()), () -> ANSWER)));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, refusal.getStatusCode());
            assertEquals(ExceptionUpstreamError.SERVER_BUSY.getCode(), refusal.getErrorCode());
        }

        @Test
        void run_refusedByTheInstance_givesThePermitOfTheOrganizationBack() {
            LessonDeliveryBulkhead bulkhead = bulkhead();
            UUID organizationId = UUID.randomUUID();

            runNested(bulkhead, fillTheInstance(), () -> {
                for (int attempt = 0; attempt <= LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX; attempt++) {
                    // Each refusal must be 503: a permit of the organization kept by an earlier refusal
                    // would turn a later one into 429.
                    assertThrows(ServiceUnavailableException.class, () -> bulkhead.run(machineOf(organizationId), () -> ANSWER));
                }
                return ANSWER;
            });

            assertEquals(0, bulkhead.organizationsReading());
        }
    }

    @Nested
    class Release {

        private void assertBothCapsAreFree(LessonDeliveryBulkhead bulkhead) {
            assertEquals(0, bulkhead.organizationsReading());
            assertEquals(ANSWER, runNested(bulkhead, fillTheInstance(), () -> ANSWER));
            assertEquals(ANSWER, runNested(bulkhead, fillTheOrganization(UUID.randomUUID()), () -> ANSWER));
        }

        @Test
        void run_afterReadsThatSucceeded_givesEveryPermitBack() {
            LessonDeliveryBulkhead bulkhead = bulkhead();
            UUID organizationId = UUID.randomUUID();
            for (int read = 0; read < LessonConstant.DELIVERY_CONCURRENT_READS_MAX * 3; read++) {
                assertEquals(ANSWER, bulkhead.run(machineOf(organizationId), () -> ANSWER));
            }

            assertBothCapsAreFree(bulkhead);
            assertEquals(ANSWER, runNested(bulkhead, fillTheOrganization(organizationId), () -> ANSWER));
        }

        @Test
        void run_afterReadsThatFailed_givesEveryPermitBack() {
            LessonDeliveryBulkhead bulkhead = bulkhead();
            IllegalStateException failure = new IllegalStateException("read failed");

            for (int read = 0; read < LessonConstant.DELIVERY_CONCURRENT_READS_MAX * 3; read++) {
                IllegalStateException thrown = assertThrows(IllegalStateException.class,
                        () -> runNested(bulkhead, fillTheInstance(), () -> {
                            throw failure;
                        }));
                assertSame(failure, thrown);
            }

            assertBothCapsAreFree(bulkhead);
        }

        @Test
        void run_afterReadsThatTimedOut_givesEveryPermitBack() {
            LessonDeliveryBulkhead bulkhead = bulkhead();
            UUID organizationId = UUID.randomUUID();

            for (int read = 0; read < LessonConstant.DELIVERY_CONCURRENT_READS_MAX * 3; read++) {
                assertThrows(QueryTimeoutException.class, () -> runNested(bulkhead, fillTheOrganization(organizationId), () -> {
                    throw new QueryTimeoutException("canceling statement due to statement timeout");
                }));
            }

            assertBothCapsAreFree(bulkhead);
            assertEquals(ANSWER, runNested(bulkhead, fillTheOrganization(organizationId), () -> ANSWER));
        }

        @Test
        void run_afterReadsOfManyOrganizations_keepsNoEntryInTheTable() {
            LessonDeliveryBulkhead bulkhead = bulkhead();

            for (int organization = 0; organization < 1_000; organization++) {
                bulkhead.run(machineOf(UUID.randomUUID()), () -> ANSWER);
            }

            assertEquals(0, bulkhead.organizationsReading());
        }
    }
}
