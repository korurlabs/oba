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
     * of the git email, connects the repository, learns the Claude account, applies the batch to the
     * session, then locks the machine, records it as seen and stores the usage snapshot. The batch belongs to
     * the token's machine and user whatever machine name and git email it reports; a git email of
     * another member flags the session for review. A batch with usage but no events for a session
     * Kaya has not recorded yet creates no session. A handoff or plan whose content holds U+0000 is
     * a binary file and is left out, and U+0000 in any other text is stored as U+FFFD, as
     * {@link com.korur.oba.apps.kaya.ingest.helper.IngestTextSanitizer} describes.
     *
     * @param principal the authenticated machine token
     * @param received  the batch as received
     * @return the session and the highest seq Kaya holds for it
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_VAL_001 when the
     *         session belongs to another user
     * @throws com.korur.oba.dojo.exception.internal.business.TooManyRequestsException KAY_SEC_004
     *         when the token sent more than 120 batches in a minute
     * @throws com.korur.oba.dojo.exception.internal.business.BadRequestException KAY_VAL_013 for an
     *         invalid event, KAY_VAL_014 for a batch without events and usage, KAY_VAL_009 for usage
     *         out of range
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 when
     *         the repository is archived
     * @throws com.korur.oba.dojo.exception.internal.auth.UnauthorizedException KAY_SEC_003 when the
     *         machine was retired after the token was checked; nothing of the batch is stored
     */
    IngestBatchResponse ingestBatch(MachinePrincipal principal, IngestBatchRequest received);
}
