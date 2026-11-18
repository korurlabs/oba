package com.korur.oba.apps.kaya.plan.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.UserDisplayNameHelper;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.plan.constant.PlanConstant;
import com.korur.oba.apps.kaya.plan.constant.service.PlanServiceLogConstant;
import com.korur.oba.apps.kaya.plan.dto.request.PlanFeedbackRequest;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackResponse;
import com.korur.oba.apps.kaya.plan.entity.Plan;
import com.korur.oba.apps.kaya.plan.entity.PlanFeedback;
import com.korur.oba.apps.kaya.plan.entity.PlanVersion;
import com.korur.oba.apps.kaya.plan.enums.PlanFeedbackDecision;
import com.korur.oba.apps.kaya.plan.enums.PlanReviewState;
import com.korur.oba.apps.kaya.plan.mapper.PlanMapper;
import com.korur.oba.apps.kaya.plan.repository.PlanFeedbackRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanVersionRepository;
import com.korur.oba.apps.kaya.plan.service.PlanService;

@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PlanServiceImpl.class);
    private static final List<PlanFeedbackDecision> DECIDING = List.of(PlanFeedbackDecision.APPROVE,
            PlanFeedbackDecision.REQUEST_CHANGES);

    private final PlanRepository planRepository;
    private final PlanVersionRepository planVersionRepository;
    private final PlanFeedbackRepository planFeedbackRepository;
    private final UserAccessHelper userAccessHelper;
    private final PlanMapper planMapper;
    private final LoggingService loggingService;
    private final Clock clock;

    @Override
    @Transactional
    public PlanFeedbackResponse giveFeedback(User user, UUID organizationId, UUID planId, PlanFeedbackRequest request) {
        // The plan row is locked before the version is checked, so a version stored by the ingest
        // meanwhile either commits first and makes this version old, or waits for this decision.
        Plan plan = lockedPlan(organizationId, planId);
        PlanVersion version = planVersionRepository.findByPlanIdAndNumber(plan.getId(), request.versionNumber()).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.VERSION_NOT_FOUND, request.versionNumber(), planId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_PLAN_FOUND,
                    Map.of(PlanConstant.FIELD_PLAN_ID, planId.toString(), PlanConstant.FIELD_VERSION_NUMBER, request.versionNumber()));
        });
        requireValidFeedback(user, plan, version, request);
        requireLatestForDecision(user, plan, version, request.decision());
        if (request.decision() == PlanFeedbackDecision.APPROVE) {
            userAccessHelper.requireAdminOrSelf(user, organizationId, plan.getUserId());
        }

        PlanFeedback feedback = planFeedbackRepository.save(
                planMapper.toFeedbackEntity(plan.getId(), version.getId(), user.getId(), request));
        switch (request.decision()) {
            case APPROVE -> plan.setReviewState(PlanReviewState.APPROVED);
            case REQUEST_CHANGES -> plan.setReviewState(PlanReviewState.CHANGES_REQUESTED);
            case COMMENT -> {
                // A comment is review input, not a decision; the state stays as it is.
            }
        }
        plan.setOpenFeedbackCount(plan.getOpenFeedbackCount() + 1);
        planRepository.save(plan);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.FEEDBACK_RECORDED,
                user.getId(), request.decision(), planId, version.getNumber(), plan.getReviewState());
        return planMapper.toFeedbackResponse(feedback, version.getNumber(),
                planMapper.toReferenceResponse(user.getId(), UserDisplayNameHelper.displayNameOf(user)));
    }

    @Override
    @Transactional
    public void withdrawFeedback(User user, UUID organizationId, UUID planId, UUID feedbackId) {
        Plan plan = lockedPlan(organizationId, planId);
        PlanFeedback feedback = planFeedbackRepository.findByIdAndPlanId(feedbackId, plan.getId()).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.FEEDBACK_NOT_FOUND, feedbackId, planId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_PLAN_FOUND,
                    Map.of(PlanConstant.FIELD_PLAN_ID, planId.toString(), PlanConstant.FIELD_FEEDBACK_ID, feedbackId.toString()));
        });
        if (!feedback.getReviewerId().equals(user.getId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.FEEDBACK_NOT_OWN,
                    user.getId(), feedbackId, feedback.getReviewerId(), planId);
            throw new ForbiddenException(ExceptionSecurityError.INCORRECT_ROLE,
                    Map.of(PlanConstant.FIELD_FEEDBACK_ID, feedbackId.toString()));
        }
        if (feedback.getDeliveredAt() != null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.FEEDBACK_ALREADY_DELIVERED,
                    feedbackId, planId, feedback.getDeliveredSessionId(), user.getId());
            throw new ConflictException(ExceptionDomainKayaError.PLN_FEEDBACK_DELIVERED,
                    Map.of(PlanConstant.FIELD_FEEDBACK_ID, feedbackId.toString()));
        }

        planFeedbackRepository.delete(feedback);
        planFeedbackRepository.flush();
        plan.setOpenFeedbackCount(Math.max(plan.getOpenFeedbackCount() - 1, 0));
        if (DECIDING.contains(feedback.getDecision())) {
            plan.setReviewState(reviewStateOfLatestVersion(plan));
        }
        planRepository.save(plan);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.FEEDBACK_WITHDRAWN,
                user.getId(), feedbackId, planId, plan.getReviewState());
    }

    @Override
    @Transactional
    public int acknowledge(MachinePrincipal principal, String sessionId, List<UUID> feedbackIds) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.ACKNOWLEDGING_FEEDBACK,
                feedbackIds.size(), sessionId, principal.userId(), principal.organizationId());
        Set<UUID> ids = new HashSet<>(feedbackIds);
        Instant deliveredAt = Instant.now(clock);
        int acknowledged = 0;
        // Plans are locked in id order, so two acknowledgements of overlapping plans never wait on
        // each other in a cycle.
        for (UUID planId : new TreeSet<>(planFeedbackRepository.findPlanIdsOfPending(ids))) {
            // The lock is taken before the rows are marked, so a concurrent acknowledgement or
            // withdrawal of the same rows sees them marked and lowers the count only once.
            Optional<Plan> locked = planRepository.findByIdAndOrganizationIdForUpdate(planId, principal.organizationId());
            if (locked.isEmpty() || !locked.get().getUserId().equals(principal.userId())) {
                continue;
            }
            Plan plan = locked.get();
            int marked = planFeedbackRepository.markDelivered(plan.getId(), ids, sessionId, deliveredAt);
            if (marked == 0) {
                continue;
            }
            plan.setOpenFeedbackCount(Math.max(plan.getOpenFeedbackCount() - marked, 0));
            planRepository.save(plan);
            acknowledged += marked;
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.FEEDBACK_ACKNOWLEDGED,
                    marked, plan.getId(), sessionId, plan.getOpenFeedbackCount());
        }
        return acknowledged;
    }

    private Plan lockedPlan(UUID organizationId, UUID planId) {
        return planRepository.findByIdAndOrganizationIdForUpdate(planId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_NOT_FOUND, planId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_PLAN_FOUND,
                    Map.of(PlanConstant.FIELD_PLAN_ID, planId.toString()));
        });
    }

    /**
     * The rules a feedback must meet on its own version: a comment unless it approves, and a line
     * range that has both bounds in order within the version's lines, or no range at all.
     */
    private void requireValidFeedback(User user, Plan plan, PlanVersion version, PlanFeedbackRequest request) {
        if (request.decision() != PlanFeedbackDecision.APPROVE && request.comment() == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.FEEDBACK_COMMENT_MISSING,
                    request.decision(), plan.getId(), user.getId());
            throw new ValidationException(ExceptionDomainKayaError.PLN_FEEDBACK_INVALID,
                    Map.of(PlanConstant.FIELD_COMMENT, PlanConstant.COMMENT_REQUIRED_MESSAGE));
        }
        Integer start = request.lineStart();
        Integer end = request.lineEnd();
        if (start == null && end == null) {
            return;
        }
        String refusal = null;
        String field = PlanConstant.FIELD_LINE_END;
        if (start == null || end == null) {
            refusal = PlanConstant.LINE_BOUNDS_PAIRED_MESSAGE;
            field = start == null ? PlanConstant.FIELD_LINE_START : PlanConstant.FIELD_LINE_END;
        } else if (end < start) {
            refusal = PlanConstant.LINE_BOUNDS_ORDER_MESSAGE;
        } else if (end > version.getLineCount()) {
            refusal = PlanConstant.LINE_BOUNDS_RANGE_MESSAGE;
        }
        if (refusal == null) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.FEEDBACK_LINES_INVALID,
                plan.getId(), version.getNumber(), user.getId(), start, end, version.getLineCount());
        Map<String, Object> errorValues = new HashMap<>();
        errorValues.put(field, refusal);
        errorValues.put(PlanConstant.FIELD_LINE_COUNT, version.getLineCount());
        throw new ValidationException(ExceptionDomainKayaError.PLN_FEEDBACK_INVALID, errorValues);
    }

    /**
     * A decision always speaks about the latest version; an older version takes only comments, so
     * a decision never lands on content Claude has already replaced.
     */
    private void requireLatestForDecision(User user, Plan plan, PlanVersion version, PlanFeedbackDecision decision) {
        if (decision == PlanFeedbackDecision.COMMENT || version.getNumber() == plan.getLatestVersionNumber()) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.FEEDBACK_VERSION_NOT_LATEST,
                decision, plan.getId(), version.getNumber(), user.getId(), plan.getLatestVersionNumber());
        throw new ConflictException(ExceptionDomainKayaError.PLN_VERSION_NOT_LATEST,
                Map.of(PlanConstant.FIELD_VERSION_NUMBER, version.getNumber(),
                        PlanConstant.FIELD_LATEST_VERSION_NUMBER, plan.getLatestVersionNumber()));
    }

    /** The last APPROVE or REQUEST_CHANGES on the latest version decides the state; without one it is OPEN. */
    private PlanReviewState reviewStateOfLatestVersion(Plan plan) {
        return planVersionRepository.findByPlanIdAndNumber(plan.getId(), plan.getLatestVersionNumber())
                .flatMap(latest -> planFeedbackRepository.findFirstByVersionIdAndDecisionInOrderByCreatedAtDescIdDesc(
                        latest.getId(), DECIDING))
                .map(decision -> decision.getDecision() == PlanFeedbackDecision.APPROVE
                        ? PlanReviewState.APPROVED : PlanReviewState.CHANGES_REQUESTED)
                .orElse(PlanReviewState.OPEN);
    }
}
