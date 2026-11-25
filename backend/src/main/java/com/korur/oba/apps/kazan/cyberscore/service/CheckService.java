package com.korur.oba.apps.kazan.cyberscore.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckOutcomeRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckStatusRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CheckResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service of an organization's checks. The caller's
 * {@link com.korur.oba.shrine.organization.enums.OrganizationMemberRole#ADMIN}
 * role in the organization is checked before these methods run.
 *
 * A create, a delete and every change of weight, category or outcome
 * recalculates the organization's cyberscore in the same transaction; a change
 * of name, texts, sort order or status does not.
 */
public interface CheckService {

    /**
     * Creates a check and recalculates the score.
     *
     * @param user           the authenticated user, recorded on the score snapshot
     * @param organizationId the organization of the request
     * @param request        the check fields
     * @return the created check
     */
    CheckResponse createCheck(User user, UUID organizationId, CheckRequest request);

    /**
     * Replaces the fields of a check, and recalculates the score when weight,
     * category or outcome changed.
     *
     * @param user           the authenticated user, recorded on the score snapshot
     * @param organizationId the organization of the request
     * @param checkId        the check identifier
     * @param request        the check fields
     * @return the updated check
     */
    CheckResponse updateCheck(User user, UUID organizationId, UUID checkId, CheckRequest request);

    /**
     * Records a check's current outcome, sets the status it implies and
     * recalculates the score when the outcome changed.
     *
     * @param user           the authenticated user, recorded on the score snapshot
     * @param organizationId the organization of the request
     * @param checkId        the check identifier
     * @param request        the new outcome
     * @return the updated check
     */
    CheckResponse setCheckOutcome(User user, UUID organizationId, UUID checkId, CheckOutcomeRequest request);

    /**
     * Sets a check's workflow status; the score does not change.
     *
     * @param user           the authenticated user
     * @param organizationId the organization of the request
     * @param checkId        the check identifier
     * @param request        the new status
     * @return the updated check
     */
    CheckResponse setCheckStatus(User user, UUID organizationId, UUID checkId, CheckStatusRequest request);

    /**
     * Deletes a check and recalculates the score.
     *
     * @param user           the authenticated user, recorded on the score snapshot
     * @param organizationId the organization of the request
     * @param checkId        the check identifier
     */
    void deleteCheck(User user, UUID organizationId, UUID checkId);
}
