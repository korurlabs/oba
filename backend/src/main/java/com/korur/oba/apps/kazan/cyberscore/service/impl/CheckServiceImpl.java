package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.kazan.cyberscore.constant.service.CheckServiceLogConstant.*;

import com.korur.oba.apps.kazan.cyberscore.constant.CheckConstant;
import com.korur.oba.apps.kazan.cyberscore.constant.CheckExceptionLogConstant;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckOutcomeRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckStatusRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CheckResponse;
import com.korur.oba.apps.kazan.cyberscore.entity.Check;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckOutcome;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckStatus;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreSnapshotEventType;
import com.korur.oba.apps.kazan.cyberscore.mapper.CheckMapper;
import com.korur.oba.apps.kazan.cyberscore.repository.CheckRepository;
import com.korur.oba.apps.kazan.cyberscore.service.CheckService;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreService;
import com.korur.oba.apps.kazan.cyberscore.validator.CheckValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements writes to an organization's checks and keeps its cyberscore in
 * step with them.
 *
 * Every write first takes the organization's scoring lock, and only then loads
 * the check. The check has no version column and is written with all its
 * columns, so a write that loaded the check before a concurrent scoring change
 * committed would put the old weight, category or outcome back without a
 * recalculation. Holding the lock first makes each write see the committed
 * check, which keeps the stored score equal to the formula over the committed
 * checks. Only creates, deletes and changes of weight, category or outcome
 * recalculate and may write a snapshot; text, sort order and status changes do
 * not.
 *
 * The organization ADMIN role is checked once per request by the access
 * interceptor, and a check of another organization reads as missing.
 */
@Service
@RequiredArgsConstructor
public class CheckServiceImpl implements CheckService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CheckServiceImpl.class);

    private final CheckRepository checkRepository;
    private final CheckMapper checkMapper;
    private final CheckValidator checkValidator;
    private final CyberscoreService cyberscoreService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public CheckResponse createCheck(User user, UUID organizationId, CheckRequest request) {
        cyberscoreService.lockScore(organizationId);
        checkValidator.ensureNameIsUnique(organizationId, request.name());

        CheckOutcome outcome = request.outcome() != null ? request.outcome() : CheckOutcome.TODO;
        CheckStatus status = request.status() != null ? request.status() : deriveStatus(outcome);

        Check check = checkMapper.toEntity(request);
        check.setOrganizationId(organizationId);
        check.setOutcome(outcome);
        check.setStatus(status);
        check = saveCheck(check, organizationId);

        cyberscoreService.recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_CREATED, user.getId());

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CHECK_CREATED, check.getId(), organizationId,
                check.getWeight(), check.getCategory(), outcome, status);

        return checkMapper.toResponse(check);
    }

    @Override
    @Transactional
    public CheckResponse updateCheck(User user, UUID organizationId, UUID checkId, CheckRequest request) {
        cyberscoreService.lockScore(organizationId);
        Check check = checkValidator.ensureCheckExists(organizationId, checkId);
        checkValidator.ensureNameIsUnique(organizationId, request.name(), checkId);

        // A missing outcome keeps the current one, so an edit of the texts alone
        // cannot reset a recorded outcome.
        CheckOutcome outcome = request.outcome() != null ? request.outcome() : check.getOutcome();
        boolean outcomeChanged = outcome != check.getOutcome();
        boolean weightOrCategoryChanged = !Objects.equals(request.weight(), check.getWeight())
                || request.category() != check.getCategory();
        CheckStatus status = resolveStatus(request.status(), outcome, outcomeChanged, check.getStatus());

        checkMapper.updateEntity(check, request);
        check.setOutcome(outcome);
        check.setStatus(status);
        check = saveCheck(check, organizationId);

        Optional<CyberscoreSnapshotEventType> scoreEvent = scoreEvent(weightOrCategoryChanged, outcomeChanged);
        if (scoreEvent.isPresent()) {
            cyberscoreService.recalculate(organizationId, scoreEvent.get(), user.getId());
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CHECK_UPDATED, checkId, organizationId,
                scoreEvent.orElse(null));

        return checkMapper.toResponse(check);
    }

    @Override
    @Transactional
    public CheckResponse setCheckOutcome(User user, UUID organizationId, UUID checkId, CheckOutcomeRequest request) {
        cyberscoreService.lockScore(organizationId);
        Check check = checkValidator.ensureCheckExists(organizationId, checkId);

        boolean outcomeChanged = request.outcome() != check.getOutcome();
        // Recording an outcome always sets the status it implies, also when the
        // outcome stays the same, so the list shows the check as handled.
        CheckStatus status = deriveStatus(request.outcome());

        check.setOutcome(request.outcome());
        check.setStatus(status);
        check = checkRepository.saveAndFlush(check);

        if (outcomeChanged) {
            cyberscoreService.recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_OUTCOME_CHANGED,
                    user.getId());
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CHECK_OUTCOME_SET, checkId, organizationId,
                request.outcome(), status, outcomeChanged);

        return checkMapper.toResponse(check);
    }

    @Override
    @Transactional
    public CheckResponse setCheckStatus(User user, UUID organizationId, UUID checkId, CheckStatusRequest request) {
        // No recalculation follows, but the lock still orders this write after a
        // concurrent scoring change, whose outcome it would otherwise overwrite.
        cyberscoreService.lockScore(organizationId);
        Check check = checkValidator.ensureCheckExists(organizationId, checkId);

        check.setStatus(request.status());
        check = checkRepository.saveAndFlush(check);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CHECK_STATUS_SET, checkId, organizationId,
                request.status());

        return checkMapper.toResponse(check);
    }

    @Override
    @Transactional
    public void deleteCheck(User user, UUID organizationId, UUID checkId) {
        cyberscoreService.lockScore(organizationId);
        Check check = checkValidator.ensureCheckExists(organizationId, checkId);

        checkRepository.delete(check);
        checkRepository.flush();

        cyberscoreService.recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_DELETED, user.getId());

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CHECK_DELETED, checkId, organizationId);
    }

    /**
     * The status a check gets with an outcome: TODO has not been started,
     * NOT_APPLICABLE stays not applicable, and any answered outcome is done.
     */
    private static CheckStatus deriveStatus(CheckOutcome outcome) {
        return switch (outcome) {
            case TODO -> CheckStatus.NOT_STARTED;
            case NOT_APPLICABLE -> CheckStatus.NOT_APPLICABLE;
            case PASS, PARTIAL, FAIL, NO_LICENSE -> CheckStatus.DONE;
        };
    }

    /**
     * The status of an edited check: the one the request names wins, a changed
     * outcome derives a new one, and otherwise the current status stays.
     */
    private static CheckStatus resolveStatus(CheckStatus requested, CheckOutcome outcome, boolean outcomeChanged,
                                             CheckStatus current) {
        if (requested != null) {
            return requested;
        }
        return outcomeChanged ? deriveStatus(outcome) : current;
    }

    /**
     * The snapshot event of an edit, or empty when the edit leaves the score
     * inputs alone. A weight or category change counts as an update whether or
     * not the outcome changed with it.
     */
    private static Optional<CyberscoreSnapshotEventType> scoreEvent(boolean weightOrCategoryChanged,
                                                                    boolean outcomeChanged) {
        if (weightOrCategoryChanged) {
            return Optional.of(CyberscoreSnapshotEventType.CHECK_UPDATED);
        }
        if (outcomeChanged) {
            return Optional.of(CyberscoreSnapshotEventType.CHECK_OUTCOME_CHANGED);
        }
        return Optional.empty();
    }

    /**
     * Writes a check at once, so a concurrent write that took the same name
     * after the name check is caught here and answered with the conflict the
     * name check gives. Every other integrity failure goes up as it is.
     *
     * @throws ConflictException STA_CHECK_NAME_DUPLICATE when the name constraint refuses the write
     */
    private Check saveCheck(Check check, UUID organizationId) {
        try {
            return checkRepository.saveAndFlush(check);
        } catch (DataIntegrityViolationException exception) {
            if (!isNameConflict(exception)) {
                throw exception;
            }
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, CheckExceptionLogConstant.CHECK_NAME_RACE_LOST,
                    check.getName(), organizationId, exception.getMostSpecificCause().getMessage());
            throw new ConflictException(ExceptionDomainKazanError.STA_CHECK_NAME_DUPLICATE,
                    Map.of(CheckConstant.FIELD_NAME, check.getName()));
        }
    }

    /**
     * Tells whether an integrity failure is the unique constraint on
     * organization and name, and not any other constraint of the check.
     */
    private static boolean isNameConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return CheckConstant.NAME_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}
