package com.korur.oba.apps.kaya.plan.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.plan.dto.request.PlanFeedbackDeliveryQueryRequest;
import com.korur.oba.apps.kaya.plan.dto.request.PlanQueryRequest;
import com.korur.oba.apps.kaya.plan.dto.response.PlanDetailResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackDeliveryResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFiltersResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanListResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanVersionContentResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanVersionDiffResponse;
import com.korur.oba.apps.kaya.plan.entity.Plan;

/**
 * Reads the plans of an organization for the plans list and the plan page, and the feedback that
 * waits for delivery to the CLI. A plan or version of another organization answers the same 404 as
 * an unknown id, so ids leak nothing; the machine reads are limited to the token's organization and
 * user and answer an empty list for anything else.
 */
public interface PlanQueryService {

    /**
     * Lists the plans of the organization, newest change first.
     *
     * @param organizationId the organization of the request
     * @param request        paging (25 by default, at most 100) and filters
     * @return one page of plans with the names of their repository, pack and user
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         request names a session the organization does not have
     */
    Page<PlanListResponse> listPlans(UUID organizationId, PlanQueryRequest request);

    /**
     * The options of the list's repository, pack and user filters: the values the
     * organization's plans hold.
     *
     * @param organizationId the organization of the request
     * @return the options, each list ordered by name
     */
    PlanFiltersResponse getFilters(UUID organizationId);

    /**
     * Reads one plan with its versions, its feedback, the handoffs of its feature and whether the
     * user may approve it.
     *
     * @param user           the signed-in user
     * @param organizationId the organization of the request
     * @param planId         the plan
     * @return the plan
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such plan
     */
    PlanDetailResponse getPlan(User user, UUID organizationId, UUID planId);

    /**
     * The content of one version of a plan.
     *
     * @param organizationId the organization of the request
     * @param planId         the plan
     * @param number         the version number
     * @return the version's number and content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such plan or the plan no such version
     */
    PlanVersionContentResponse getVersion(UUID organizationId, UUID planId, int number);

    /**
     * The unified diff of one version of a plan against the version before it.
     *
     * @param organizationId the organization of the request
     * @param planId         the plan
     * @param number         the version number
     * @return the version's number and diff; the diff is empty for version 1
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such plan or the plan no such version
     */
    PlanVersionDiffResponse getDiff(UUID organizationId, UUID planId, int number);

    /**
     * Resolves a plan within the organization.
     *
     * @param organizationId the organization the plan must belong to
     * @param planId         the plan
     * @return the plan
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 otherwise
     */
    Plan getEntity(UUID organizationId, UUID planId);

    /**
     * Whether any plan of the organization is filed under a pack, which it was matched to by the
     * pack's name.
     *
     * @param organizationId the organization of the pack
     * @param packId         the pack
     * @return true when at least one plan names the pack
     */
    boolean existsForPack(UUID organizationId, UUID packId);

    /**
     * The pending feedback the CLI asks for, in one of two forms: by session ids
     * ({@link #pendingForSessions}) or by remote and branch ({@link #pendingForBranch}).
     *
     * @param principal the machine token's principal
     * @param request   either session ids, or a remote URL and a branch
     * @return the pending feedback, oldest first, at most 50
     * @throws com.korur.oba.dojo.exception.internal.business.BadRequestException KAY_PLN_004 when
     *         the request gives neither form or both
     */
    List<PlanFeedbackDeliveryResponse> getPendingFeedback(MachinePrincipal principal, PlanFeedbackDeliveryQueryRequest request);

    /**
     * The feedback no session has received yet on the plans of the token's user whose latest
     * version came from one of the given sessions. Unknown session ids, and sessions of another
     * organization, match nothing.
     *
     * @param principal  the machine token's principal
     * @param sessionIds Claude Code session ids
     * @return the pending feedback, oldest first, at most 50
     */
    List<PlanFeedbackDeliveryResponse> pendingForSessions(MachinePrincipal principal, List<String> sessionIds);

    /**
     * The feedback no session has received yet on the plans of the token's user in the
     * checkout's repository whose latest session has ended on the given branch, so a new session
     * on that branch picks up what an ended one missed. A remote the organization does not know, or
     * one Kaya cannot read, answers an empty list.
     *
     * @param principal the machine token's principal
     * @param remoteUrl the checkout's git remote, raw
     * @param branch    the checkout's branch
     * @return the pending feedback, oldest first, at most 50
     */
    List<PlanFeedbackDeliveryResponse> pendingForBranch(MachinePrincipal principal, String remoteUrl, String branch);
}
