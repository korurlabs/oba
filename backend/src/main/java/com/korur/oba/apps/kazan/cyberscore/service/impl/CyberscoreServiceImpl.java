package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.kazan.cyberscore.constant.service.CyberscoreServiceLogConstant.*;

import com.korur.oba.apps.kazan.cyberscore.constant.CyberscoreConstant;
import com.korur.oba.apps.kazan.cyberscore.constant.CyberscoreExceptionLogConstant;
import com.korur.oba.apps.kazan.cyberscore.dto.derived.CyberscoreCalculation;
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreSnapshot;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreSnapshotEventType;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreCheckRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreSnapshotRepository;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCalculateService;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreService;
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implements the scoring lock and the recalculation of an organization's cyberscore.
 *
 * Both methods join the check write's transaction and refuse to run without
 * one: a row lock taken outside a transaction would be released at once, and a
 * score written apart from its check change could disagree with the committed
 * checks. Because every scoring write of an organization holds the row lock
 * while it reads the checks, the stored score always equals the formula over
 * the committed checks, also when two admins change checks at the same moment.
 */
@Service
@RequiredArgsConstructor
public class CyberscoreServiceImpl implements CyberscoreService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CyberscoreServiceImpl.class);

    private final CyberscoreRepository cyberscoreRepository;
    private final CyberscoreSnapshotRepository cyberscoreSnapshotRepository;
    private final CyberscoreCheckRepository checkRepository;
    private final CyberscoreCalculateService cyberscoreCalculateService;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockScore(UUID organizationId) {
        // Insert first, so the first two scoring writes of an organization both
        // find a row to lock instead of one failing on the unique constraint.
        boolean inserted = cyberscoreRepository.insertIfAbsent(organizationId) > 0;
        loadLockedRow(organizationId);

        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, CYBERSCORE_LOCKED, organizationId, inserted);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recalculate(UUID organizationId, CyberscoreSnapshotEventType eventType, UUID performedById) {
        // Already locked by lockScore in this transaction, so this lookup does not wait.
        Cyberscore cyberscore = loadLockedRow(organizationId);

        // The query flushes the pending check change first, so the calculation sees it.
        List<CyberscoreCheck> checks =
                checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId);
        CyberscoreCalculation calculation = cyberscoreCalculateService.calculate(checks);

        boolean moved = hasMoved(cyberscore, calculation);
        Instant now = Instant.now(clock);

        cyberscore.setScorePercentage(calculation.scorePercentage());
        cyberscore.setScoreLevel(calculation.scoreLevel());
        cyberscore.setLastCalculatedAt(now);
        cyberscoreRepository.save(cyberscore);

        if (moved) {
            cyberscoreSnapshotRepository.save(CyberscoreSnapshot.builder()
                    .organizationId(organizationId)
                    .scorePercentage(calculation.scorePercentage())
                    .scoreLevel(calculation.scoreLevel())
                    .eventType(eventType)
                    .performedById(performedById)
                    .takenAt(now)
                    .build());
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CYBERSCORE_RECALCULATED, organizationId, eventType,
                calculation.scorePercentage(), calculation.scoreLevel(), moved);
    }

    /**
     * Loads the organization's score row under a row lock.
     *
     * @param organizationId the organization of the request
     * @return the locked row
     * @throws InternalServerErrorException when the row does not exist, which
     *         means the caller skipped {@link #lockScore(UUID)}
     */
    private Cyberscore loadLockedRow(UUID organizationId) {
        return cyberscoreRepository.findByOrganizationIdForUpdate(organizationId)
                .orElseThrow(() -> {
                    loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                            CyberscoreExceptionLogConstant.CYBERSCORE_ROW_MISSING, organizationId);
                    return new InternalServerErrorException(ExceptionInternalError.UNEXPECTED,
                            Map.of(CyberscoreConstant.FIELD_ORGANIZATION_ID, organizationId));
                });
    }

    /**
     * Whether the new result differs from the stored one in percentage or level.
     * Only such a change is worth a trend snapshot; a recalculation that lands on
     * the same score writes none.
     */
    private static boolean hasMoved(Cyberscore cyberscore, CyberscoreCalculation calculation) {
        return !Objects.equals(cyberscore.getScorePercentage(), calculation.scorePercentage())
                || cyberscore.getScoreLevel() != calculation.scoreLevel();
    }
}
