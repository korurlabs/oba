package com.korur.oba.apps.kazan.cyberscore.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreSnapshotEventType;

/**
 * Write service of an organization's cyberscore row and its snapshots.
 *
 * A check write that changes the score calls {@link #lockScore(UUID)} before it
 * changes the check and {@link #recalculate(UUID, CyberscoreSnapshotEventType, UUID)}
 * after, in the same transaction. Both methods require that transaction: the
 * lock lives until it commits, and a failed score write rolls the check write
 * back.
 */
public interface CyberscoreService {

    /**
     * Creates the organization's score row when it has none and locks it until
     * the transaction ends, so the scoring writes of one organization run one
     * after another.
     *
     * @param organizationId the organization of the request
     */
    void lockScore(UUID organizationId);

    /**
     * Scores all checks of the organization, stores the result on the locked
     * row with the calculation time, and appends a snapshot when the
     * percentage, level or breakdown differ from the stored ones.
     *
     * @param organizationId the organization of the request
     * @param eventType      the check change that caused the recalculation
     * @param performedById  the user who made the change, or null for a system change
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException
     *         when {@link #lockScore(UUID)} was not called first in the transaction
     */
    void recalculate(UUID organizationId, CyberscoreSnapshotEventType eventType, UUID performedById);
}
