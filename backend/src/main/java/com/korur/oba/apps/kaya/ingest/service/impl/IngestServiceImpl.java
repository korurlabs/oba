package com.korur.oba.apps.kaya.ingest.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.apps.kaya.account.enums.AccountProvider;
import com.korur.oba.apps.kaya.account.service.AccountService;
import com.korur.oba.apps.kaya.identity.service.UserGitEmailQueryService;
import com.korur.oba.apps.kaya.ingest.constant.IngestConstant;
import com.korur.oba.apps.kaya.ingest.constant.service.IngestServiceLogConstant;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestAccountRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestBatchRequest;
import com.korur.oba.apps.kaya.ingest.dto.response.IngestBatchResponse;
import com.korur.oba.apps.kaya.ingest.helper.IngestRateLimiter;
import com.korur.oba.apps.kaya.ingest.mapper.IngestMapper;
import com.korur.oba.apps.kaya.ingest.service.IngestService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.service.MachineService;
import com.korur.oba.apps.kaya.plan.service.PlanIngestService;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.service.SessionIngestService;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.service.RepositoryService;

@Service
@RequiredArgsConstructor
public class IngestServiceImpl implements IngestService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IngestServiceImpl.class);

    private final IngestRateLimiter ingestRateLimiter;
    private final RepositoryService repositoryService;
    private final MachineService machineService;
    private final AccountService accountService;
    private final UserGitEmailQueryService userGitEmailQueryService;
    private final SessionIngestService sessionIngestService;
    private final PlanIngestService planIngestService;
    private final IngestMapper ingestMapper;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public IngestBatchResponse ingestBatch(MachinePrincipal principal, IngestBatchRequest request) {
        List<Map<String, Object>> events = request.events() == null ? List.of() : request.events();
        String sessionId = request.sessionId();
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, IngestServiceLogConstant.INGESTING_BATCH, sessionId,
                principal.machineId(), events.size(), request.handoffs() == null ? 0 : request.handoffs().size(),
                request.plans() == null ? 0 : request.plans().size(), request.usage() != null);

        logDifferentMachineName(principal, request);
        if (events.isEmpty() && request.usage() == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, IngestServiceLogConstant.BATCH_EMPTY, sessionId, principal.machineId());
            throw new BadRequestException(ExceptionDomainKayaError.VAL_BATCH_EMPTY,
                    Map.of(IngestConstant.FIELD_SESSION_ID, sessionId));
        }
        validateEvents(principal, sessionId, events);
        if (!ingestRateLimiter.tryCharge(principal.tokenId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, IngestServiceLogConstant.RATE_LIMITED,
                    sessionId, principal.tokenId(), IngestConstant.BATCHES_PER_MINUTE);
            throw new TooManyRequestsException(ExceptionDomainKayaError.SEC_RATE_LIMITED,
                    Map.of(IngestConstant.FIELD_LIMIT, IngestConstant.BATCHES_PER_MINUTE));
        }

        // Resolved before the first write, so a batch of an unregistered git email stores nothing.
        UUID conflictUserId = identityConflict(principal, sessionId, gitEmailHolder(principal, sessionId,
                request.summary().gitAuthorEmail()));

        Repository repository = repositoryService.connectRepository(principal.organizationId(),
                request.repository().remoteUrl(), request.repository().name());

        Account account = learnAccount(principal, request.machine().account());
        Machine machine = machineService.recordSeen(principal, request.machine().claudeCodeVersion(), account);
        if (request.usage() != null) {
            machineService.recordUsage(machine, account, sessionId, request.usage());
        }

        long acknowledgedSeq = applyToSession(principal, request, repository, conflictUserId, events);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, IngestServiceLogConstant.BATCH_INGESTED,
                sessionId, principal.machineId(), acknowledgedSeq);
        return ingestMapper.toBatchResponse(sessionId, acknowledgedSeq);
    }

    /**
     * Logs a batch whose machine name is not the registered name of the token's machine, and never
     * refuses it. The token alone identifies the machine, and the batch is attributed to that
     * machine. The name is only a display label: the recorder reports the host name, and host names
     * are neither unique nor stable, since macOS changes them with the network. A difference is
     * therefore normal, so it is logged at debug level and not as a warning on every batch.
     */
    private void logDifferentMachineName(MachinePrincipal principal, IngestBatchRequest request) {
        String machineName = request.machine().machineName();
        if (machineName.equals(principal.machineName())) {
            return;
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, IngestServiceLogConstant.MACHINE_NAME_DIFFERENT,
                request.sessionId(), principal.machineId(), principal.machineName(), machineName);
    }

    /**
     * Checks the keys Kaya stores in columns; every other key of an event is stored as sent.
     */
    private void validateEvents(MachinePrincipal principal, String sessionId, List<Map<String, Object>> events) {
        for (int index = 0; index < events.size(); index++) {
            Map<String, Object> event = events.get(index);
            if (event == null) {
                throw invalidEvent(principal, sessionId, index, IngestConstant.EVENT_NAME);
            }
            Object seq = event.get(IngestConstant.EVENT_SEQ);
            if (!(seq instanceof Integer || seq instanceof Long) || ((Number) seq).longValue() < 1) {
                throw invalidEvent(principal, sessionId, index, IngestConstant.EVENT_SEQ);
            }
            if (!(event.get(IngestConstant.EVENT_AT) instanceof String at) || !parsesAsInstant(at)) {
                throw invalidEvent(principal, sessionId, index, IngestConstant.EVENT_AT);
            }
            if (!(event.get(IngestConstant.EVENT_NAME) instanceof String name) || name.isBlank()
                    || name.length() > IngestConstant.EVENT_NAME_MAX_LENGTH) {
                throw invalidEvent(principal, sessionId, index, IngestConstant.EVENT_NAME);
            }
            Object agentId = event.get(IngestConstant.EVENT_AGENT_ID);
            if (agentId != null && (!(agentId instanceof String text)
                    || text.length() > IngestConstant.EVENT_AGENT_ID_MAX_LENGTH)) {
                throw invalidEvent(principal, sessionId, index, IngestConstant.EVENT_AGENT_ID);
            }
        }
    }

    private static boolean parsesAsInstant(String at) {
        try {
            Instant.parse(at);
            return true;
        } catch (DateTimeParseException e) {
            // rule-10-no-op -- reason: an unparsable time is answered as KAY_VAL_013 by the caller
            return false;
        }
    }

    private BadRequestException invalidEvent(MachinePrincipal principal, String sessionId, int index, String field) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, IngestServiceLogConstant.EVENT_INVALID,
                sessionId, principal.machineId(), index, field);
        return new BadRequestException(ExceptionDomainKayaError.VAL_EVENT_INVALID,
                Map.of(IngestConstant.FIELD_INDEX, index, IngestConstant.FIELD_FIELD, field));
    }

    /**
     * Learns the account of the batch. The recorder reads only Claude Code's own account file, so
     * every reported account is a Claude one.
     */
    private Account learnAccount(MachinePrincipal principal, IngestAccountRequest account) {
        if (account == null || account.emailAddress() == null || account.emailAddress().isBlank()) {
            return null;
        }
        return accountService.learnAccount(principal.organizationId(), principal.userId(), principal.machineId(),
                AccountProvider.CLAUDE, account.emailAddress(), emptyAsNull(account.organizationUuid()), emptyAsNull(account.organizationName()),
                emptyAsNull(account.billingType()), emptyAsNull(account.organizationType()),
                emptyAsNull(account.seatTier()));
    }

    /**
     * The user that holds the git email of the batch. Kaya admits only registered people, so an
     * email no user of the organization holds refuses the batch; the recorder retries a 403, and
     * the batch is accepted once someone registers the email. Ingest never registers an email
     * itself, because the token alone does not prove whose email it is.
     *
     * @return the holder, or null when the batch reported no email or one that is not an address
     */
    private UUID gitEmailHolder(MachinePrincipal principal, String sessionId, String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.strip();
        if (trimmed.indexOf('@') < 1 || trimmed.length() > IngestConstant.EMAIL_MAX_LENGTH) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, IngestServiceLogConstant.GIT_EMAIL_IGNORED, sessionId);
            return null;
        }
        return userGitEmailQueryService.findUserIdByEmail(principal.organizationId(), trimmed).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, IngestServiceLogConstant.GIT_EMAIL_UNKNOWN,
                    sessionId, principal.machineId(), principal.organizationId());
            return new ForbiddenException(ExceptionDomainKayaError.SEC_GIT_IDENTITY_UNKNOWN,
                    Map.of(IngestConstant.FIELD_GIT_AUTHOR_EMAIL, trimmed));
        });
    }

    /**
     * The other user that holds the git email of the batch; null when the token's user holds it or
     * the batch reported none. Such a batch is still accepted, so pairing on one machine keeps
     * working, and the session is flagged for review. An account is no conflict, because several
     * users can share one account.
     */
    private UUID identityConflict(MachinePrincipal principal, String sessionId, UUID gitEmailHolder) {
        if (gitEmailHolder == null || gitEmailHolder.equals(principal.userId())) {
            return null;
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, IngestServiceLogConstant.IDENTITY_CONFLICT,
                sessionId, gitEmailHolder, principal.userId());
        return gitEmailHolder;
    }

    private long applyToSession(MachinePrincipal principal, IngestBatchRequest request, Repository repository,
            UUID conflictUserId, List<Map<String, Object>> events) {
        Session session;
        if (events.isEmpty()) {
            Optional<Session> existing = sessionIngestService.findSessionForUpdate(principal.organizationId(),
                    principal.userId(), request.sessionId());
            if (existing.isEmpty()) {
                loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, IngestServiceLogConstant.NO_SESSION_FOR_USAGE_ONLY,
                        request.sessionId(), principal.organizationId());
                return 0L;
            }
            session = existing.get();
        } else {
            session = sessionIngestService.openSession(principal.organizationId(), repository.getId(), principal.userId(),
                    principal.machineId(), request.sessionId(), startOf(request, events));
        }
        // The batch's repository, not the session's: a session keeps the repository it started in, and
        // the recorder reports the same remote for every batch of a session.
        long acknowledgedSeq = sessionIngestService.applyBatch(session, request, repository.getTicketUrlTemplate(),
                conflictUserId);
        // After the batch's handoffs are stored, so a planning handoff of this batch links the specs it names.
        planIngestService.storePlans(session, request.plans());
        return acknowledgedSeq;
    }

    /**
     * The start of a new session: the summary's start, else the first event of the batch.
     */
    private Instant startOf(IngestBatchRequest request, List<Map<String, Object>> events) {
        if (request.summary().startedAt() != null) {
            return request.summary().startedAt();
        }
        Instant earliest = null;
        for (Map<String, Object> event : events) {
            Instant at = Instant.parse((String) event.get(IngestConstant.EVENT_AT));
            if (earliest == null || at.isBefore(earliest)) {
                earliest = at;
            }
        }
        return earliest == null ? Instant.now(clock) : earliest;
    }

    private static String emptyAsNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
