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
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.constant.service.LessonServiceLogConstant;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

/**
 * Caps the lesson reads that run at the same time on this backend instance: the pages of the list and
 * the searches of the machine tokens, and the pages of the web list and its filter options. The budgets of {@link LessonDeliveryRateLimiter} bound the reads
 * that start in a minute, not the reads that hold a connection at once: once reads take seconds, a
 * few a second of one organization would hold the whole pool every tenant shares. A read therefore
 * takes a permit of its user ({@link LessonConstant#DELIVERY_USER_CONCURRENT_READS_MAX}), one of its
 * organization ({@link LessonConstant#DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX}) and then one of
 * the instance ({@link LessonConstant#DELIVERY_CONCURRENT_READS_MAX}) before its transaction takes a
 * connection, and gives all three back when it ends, however it ends. None waits: a read that finds
 * no permit is refused at once, without a connection and without queueing. A read is checked and
 * charged to its budgets before it asks, so a request refused there never takes a permit, and the
 * budgets bound how often a client asks.
 *
 * The user's cap counts their reads in every organization together. Any signed-in user can create
 * organizations, so without it one person could fill the instance with the reads of two
 * organizations of their own and lock every other tenant's reads out; with it, that takes two
 * accounts. The user is asked first and the organization next, as the budgets are charged, so the
 * surplus reads of one person never hold a permit their colleagues need, and those of one
 * organization never hold a permit of the instance another organization needs; both are answered
 * 429, which names who is over the cap. A user or an organization keeps its entry in its table only
 * while one of its reads runs, so a table never holds more entries than requests in this method.
 * Like the budgets, the caps are per instance.
 *
 * The reads of the web app share every cap with those of the machine tokens instead of holding caps
 * of their own. The instance cap exists to keep lesson reads to a part of the connection pool every
 * request shares; a second cap for the web would let both kinds together hold more of it, and a web
 * page of lessons is the same pass over them as a page of the CLI. A member who opens the list asks
 * for its page and its filter options at once, which the user's cap of two allows.
 */
@Component
@RequiredArgsConstructor
public class LessonDeliveryBulkhead {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LessonDeliveryBulkhead.class);

    private final LoggingService loggingService;
    private final Semaphore instanceReads = new Semaphore(LessonConstant.DELIVERY_CONCURRENT_READS_MAX);
    private final KeyedPermits userReads = new KeyedPermits(LessonConstant.DELIVERY_USER_CONCURRENT_READS_MAX);
    private final KeyedPermits organizationReads = new KeyedPermits(LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX);

    /**
     * Runs one lesson read of a machine token within the caps.
     *
     * @param principal the machine token that sent the read
     * @param read      the read, which opens its own transaction
     * @param <T>       the answer of the read
     * @return the answer of the read
     * @throws TooManyRequestsException KAY_SEC_004 when the user of the token already runs
     *         {@link LessonConstant#DELIVERY_USER_CONCURRENT_READS_MAX} reads on this instance, or its
     *         organization {@link LessonConstant#DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX}
     * @throws ServiceUnavailableException GEN_UPS_005 when the instance already runs
     *         {@link LessonConstant#DELIVERY_CONCURRENT_READS_MAX} reads
     */
    public <T> T run(MachinePrincipal principal, Supplier<T> read) {
        return run(principal.userId(), principal.organizationId(), read);
    }

    /**
     * Runs one lesson read of a member in the web app, a page of the list or the filter options,
     * within the same caps as the reads of the machine tokens.
     *
     * @param user           the signed-in member
     * @param organizationId the organization of the request
     * @param read           the read, which opens its own transaction
     * @param <T>            the answer of the read
     * @return the answer of the read
     * @throws TooManyRequestsException KAY_SEC_004 when the member already runs
     *         {@link LessonConstant#DELIVERY_USER_CONCURRENT_READS_MAX} reads on this instance, or the
     *         organization {@link LessonConstant#DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX}
     * @throws ServiceUnavailableException GEN_UPS_005 when the instance already runs
     *         {@link LessonConstant#DELIVERY_CONCURRENT_READS_MAX} reads
     */
    public <T> T run(User user, UUID organizationId, Supplier<T> read) {
        return run(user.getId(), organizationId, read);
    }

    private <T> T run(UUID userId, UUID organizationId, Supplier<T> read) {
        if (!userReads.tryAcquire(userId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_USER_BUSY,
                    userId, organizationId, LessonConstant.DELIVERY_USER_CONCURRENT_READS_MAX);
            throw busy(LessonConstant.DELIVERY_USER_CONCURRENT_READS_MAX, LessonConstant.POLICY_USER_READS_AT_ONCE);
        }
        try {
            if (!organizationReads.tryAcquire(organizationId)) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_ORGANIZATION_BUSY,
                        userId, organizationId, LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX);
                throw busy(LessonConstant.DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX,
                        LessonConstant.POLICY_ORGANIZATION_READS_AT_ONCE);
            }
            try {
                if (!instanceReads.tryAcquire()) {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_INSTANCE_BUSY,
                            userId, organizationId, LessonConstant.DELIVERY_CONCURRENT_READS_MAX);
                    throw new ServiceUnavailableException(ExceptionUpstreamError.SERVER_BUSY,
                            LessonConstant.DELIVERY_BUSY_RETRY_AFTER);
                }
                try {
                    return read.get();
                } finally {
                    instanceReads.release();
                }
            } finally {
                organizationReads.release(organizationId);
            }
        } finally {
            userReads.release(userId);
        }
    }

    /** The organizations with a read running on this instance; the table holds no other entry. */
    int organizationsReading() {
        return organizationReads.holders();
    }

    /** The users with a read running on this instance; the table holds no other entry. */
    int usersReading() {
        return userReads.holders();
    }

    private static TooManyRequestsException busy(int limit, String policy) {
        return new TooManyRequestsException(ExceptionDomainKayaError.SEC_RATE_LIMITED,
                Map.of(LessonConstant.FIELD_LIMIT, limit, LessonConstant.FIELD_POLICY, policy),
                LessonConstant.DELIVERY_BUSY_RETRY_AFTER);
    }

    /** A cap per key, a user or an organization, with an entry only while the key holds a permit. */
    private static final class KeyedPermits {

        private final int max;
        private final ConcurrentMap<UUID, Semaphore> permits = new ConcurrentHashMap<>();

        private KeyedPermits(int max) {
            this.max = max;
        }

        /**
         * Takes a permit of the key. The permit is taken and given back only inside an atomic update
         * of the key's entry, so an entry is never dropped while another read takes a permit from it.
         */
        private boolean tryAcquire(UUID key) {
            AtomicBoolean acquired = new AtomicBoolean();
            permits.compute(key, (id, held) -> {
                Semaphore entry = held == null ? new Semaphore(max) : held;
                acquired.set(entry.tryAcquire());
                return entry;
            });
            return acquired.get();
        }

        /** Gives a permit back and drops the entry once the key holds none. */
        private void release(UUID key) {
            permits.computeIfPresent(key, (id, held) -> {
                held.release();
                return held.availablePermits() == max ? null : held;
            });
        }

        private int holders() {
            return permits.size();
        }
    }
}
