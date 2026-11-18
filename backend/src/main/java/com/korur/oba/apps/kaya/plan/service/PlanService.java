package com.korur.oba.apps.kaya.plan.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.plan.dto.request.PlanFeedbackRequest;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackResponse;

/**
 * The writes of plans: a member's feedback and its withdrawal, and the CLI's acknowledgement that a
 * session received feedback. Each locks the plan row, so they run one after the other with a new
 * version of the plan, the review state always describes the latest version, and the open feedback
 * count stays the number of undelivered rows.
 */
public interface PlanService {

    /**
     * Records a reviewer's feedback on a version of a plan. APPROVE sets the plan APPROVED and
     * REQUEST_CHANGES sets it CHANGES_REQUESTED; COMMENT leaves the state. The feedback waits for
     * delivery, so the plan's open feedback count rises by one.
     *
     * @param user           the reviewer
     * @param organizationId the organization of the request
     * @param planId         the plan
     * @param request        the version, decision, comment and line range
     * @return the recorded feedback
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such plan or the plan no such version
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_PLN_002 for a
     *         missing comment on REQUEST_CHANGES or COMMENT, one line bound without the other, an end
     *         before the start, or a range past the version's last line
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_PLN_001 for an
     *         APPROVE or REQUEST_CHANGES on a version that is not the latest
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_003 for an
     *         APPROVE by a member who is neither the plan's user nor ADMIN or OWNER
     */
    PlanFeedbackResponse giveFeedback(User user, UUID organizationId, UUID planId, PlanFeedbackRequest request);

    /**
     * Withdraws the reviewer's own feedback while no session has received it. The open feedback
     * count falls by one, and when the feedback decided the review state, the state is recomputed
     * from the decisions left on the latest version.
     *
     * @param user           the reviewer
     * @param organizationId the organization of the request
     * @param planId         the plan
     * @param feedbackId     the feedback
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such plan or the plan no such feedback
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_003 for
     *         another member's feedback
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_PLN_003 once
     *         the feedback has been delivered
     */
    void withdrawFeedback(User user, UUID organizationId, UUID planId, UUID feedbackId);

    /**
     * Marks feedback delivered to a session of the token's user. Only rows still pending on plans
     * of the token's organization and user are marked; delivered rows and other ids are skipped,
     * so a resent acknowledgement changes nothing. Each plan's open feedback count falls by the rows
     * marked on it.
     *
     * @param principal   the machine token's principal
     * @param sessionId   the Claude Code session id of the receiving session
     * @param feedbackIds the delivered feedback
     * @return the number of rows marked now
     */
    int acknowledge(MachinePrincipal principal, String sessionId, List<UUID> feedbackIds);
}
