package com.korur.oba.apps.kaya.ingest.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.ingest.dto.request.IngestBatchRequest;
import com.korur.oba.apps.kaya.ingest.dto.response.IngestBatchResponse;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

/**
 * Takes the batches of the recorder.
 */
public interface IngestService {

    /**
     * Applies one recorder batch in one transaction: checks the token's budget, resolves the user
     * of the git email, connects the repository, records the machine as seen, learns the Claude
     * account, stores the usage snapshot and applies the batch to the session. The batch belongs to
     * the token's machine whatever machine name it reports. A batch with usage but no events for a
     * session Kaya has not recorded yet creates no session.
     *
     * @param principal the authenticated machine token
     * @param request   the batch
     * @return the session and the highest seq Kaya holds for it
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_VAL_001 when the
     *         session belongs to another user
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException KAY_SEC_007 when no
     *         user of the organization holds the reported git email; nothing is stored
     * @throws com.korur.oba.dojo.exception.internal.business.TooManyRequestsException KAY_SEC_004
     *         when the token sent more than 120 batches in a minute
     * @throws com.korur.oba.dojo.exception.internal.business.BadRequestException KAY_VAL_013 for an
     *         invalid event, KAY_VAL_014 for a batch without events and usage, KAY_VAL_009 for usage
     *         out of range
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 when
     *         the repository is archived
     */
    IngestBatchResponse ingestBatch(MachinePrincipal principal, IngestBatchRequest request);
}
