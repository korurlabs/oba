package com.korur.oba.apps.kaya.lesson.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionUpstreamError;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.exception.internal.infrastructure.ServiceUnavailableException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.constant.service.LessonServiceLogConstant;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

/**
 * Caps the lesson reads of the machine tokens, pages of the list and searches, that run at the same
 * time on this backend instance. The budgets of {@link LessonDeliveryRateLimiter} bound the reads
 * that start in a minute, not the reads that hold a connection at once: once reads take seconds, a
 * few a second of one organization would hold the whole pool every tenant shares. A read therefore
 * takes a permit of its organization ({@link LessonConstant#DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX})
 * and then one of the instance ({@link LessonConstant#DELIVERY_CONCURRENT_READS_MAX}) before its
 * transaction takes a connection, and gives both back when it ends, however it ends. Neither waits:
 * a read that finds no permit is refused at once, without a connection and without queueing.
 *
 * The organization is asked first, so an organization past its own cap is answered 429, which names
 * it, and its surplus reads never take a permit of the instance from another organization. An
 * organization keeps its entry in the table only while one of its reads runs, so the table never
 * holds more entries than requests in this method. Like the budgets, the caps are per instance.
 */
@Component
@RequiredArgsConstructor
public class LessonDeliveryBulkhead {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LessonDeliveryBulkhead.class);

    private final LoggingService loggingService;
    private final Semaphore instanceReads = new Semaphore(LessonConstant.DELIVERY_CONCURRENT_READS_MAX);
    private final ConcurrentMap<UUID, Semaphore> organizationReads = new ConcurrentHashMap<>();

    /**
     * Runs one lesson read within both caps.
     *
     * @param principal the machine token that sent the read
     * @param read      the read, which opens its own transaction
     * @param <T>       the answer of the read
     * @return the answer of the read
     * @throws TooManyRequestsException KAY_SEC_004 when the organization already runs
     *         {@link LessonConstant#DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX} reads on this instance
     * @throws ServiceUnavailableException GEN_UPS_005 when the instance already runs
     *         {@link LessonConstant#DELIVERY_CONCURRENT_READS_MAX} reads
     */
    public <T> T run(MachinePrincipal principal, Supplier<T> read) {
        UUID organizationId = principal.organizationId();
        if (!tryAcquireOrganization(organizationId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_ORGANIZATION_BUSY,
                    principal.machineId(), organizationId, LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX);
            throw new TooManyRequestsException(ExceptionDomainKayaError.SEC_RATE_LIMITED,
                    Map.of(LessonConstant.FIELD_LIMIT, LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX));
        }
        try {
            if (!instanceReads.tryAcquire()) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_INSTANCE_BUSY,
                        principal.machineId(), LessonConstant.DELIVERY_CONCURRENT_READS_MAX);
                throw new ServiceUnavailableException(ExceptionUpstreamError.SERVER_BUSY);
            }
            try {
                return read.get();
            } finally {
                instanceReads.release();
            }
        } finally {
            releaseOrganization(organizationId);
        }
    }

    /** The organizations with a read running on this instance; the table holds no other entry. */
    int organizationsReading() {
        return organizationReads.size();
    }

    /**
     * Takes a permit of the organization. The permit is taken and given back only inside an atomic
     * update of the organization's entry, so an entry is never dropped while another read takes a
     * permit from it.
     */
    private boolean tryAcquireOrganization(UUID organizationId) {
        AtomicBoolean acquired = new AtomicBoolean();
        organizationReads.compute(organizationId, (id, permits) -> {
            Semaphore held = permits == null
                    ? new Semaphore(LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX) : permits;
            acquired.set(held.tryAcquire());
            return held;
        });
        return acquired.get();
    }

    /** Gives a permit back and drops the entry once the organization runs no read. */
    private void releaseOrganization(UUID organizationId) {
        organizationReads.computeIfPresent(organizationId, (id, permits) -> {
            permits.release();
            return permits.availablePermits() == LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX ? null : permits;
        });
    }
}
