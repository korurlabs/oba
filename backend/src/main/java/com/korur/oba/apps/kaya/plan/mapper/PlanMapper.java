package com.korur.oba.apps.kaya.plan.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.plan.dto.request.PlanFeedbackRequest;
import com.korur.oba.apps.kaya.plan.dto.response.PlanDetailResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackAckResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackDeliveryResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFiltersResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanListResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanVersionContentResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanVersionDiffResponse;
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanHandoffResponse;
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanReferenceResponse;
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanSessionResponse;
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanVersionSummaryResponse;
import com.korur.oba.apps.kaya.plan.entity.Plan;
import com.korur.oba.apps.kaya.plan.entity.PlanFeedback;
import com.korur.oba.apps.kaya.plan.entity.PlanVersion;
import com.korur.oba.apps.kaya.session.dto.response.SessionHandoffResponse;
import com.korur.oba.apps.kaya.session.entity.Session;

/**
 * Maps plans, their versions and their feedback to responses. The plan keeps plain ids of other
 * domains, so the names of its repository, pack, user and reviewers and the Claude Code ids
 * of its sessions are resolved by the caller, for a whole page at once, and passed in.
 */
@Mapper(componentModel = "spring")
public interface PlanMapper {

    PlanReferenceResponse toReferenceResponse(UUID id, String name);

    @Mapping(target = "id", source = "plan.id")
    @Mapping(target = "repository", source = "repository")
    @Mapping(target = "pack", source = "pack")
    @Mapping(target = "user", source = "user")
    PlanListResponse toListResponse(Plan plan, PlanReferenceResponse repository, PlanReferenceResponse pack,
            PlanReferenceResponse user);

    @Mapping(target = "id", source = "plan.id")
    @Mapping(target = "repository", source = "repository")
    @Mapping(target = "pack", source = "pack")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "title", source = "plan.title")
    @Mapping(target = "latestSession", source = "latestSession")
    @Mapping(target = "versions", source = "versions")
    @Mapping(target = "feedback", source = "feedback")
    @Mapping(target = "handoffs", source = "handoffs")
    @Mapping(target = "planningHandoffPath", source = "planningHandoffPath")
    @Mapping(target = "canApprove", source = "canApprove")
    PlanDetailResponse toDetailResponse(Plan plan, PlanReferenceResponse repository, PlanReferenceResponse pack,
            PlanReferenceResponse user, PlanSessionResponse latestSession, List<PlanVersionSummaryResponse> versions,
            List<PlanFeedbackResponse> feedback, List<PlanHandoffResponse> handoffs, String planningHandoffPath,
            boolean canApprove);

    PlanSessionResponse toSessionResponse(Session session);

    @Mapping(target = "number", source = "version.number")
    @Mapping(target = "source", source = "version.source")
    @Mapping(target = "sessionId", source = "sessionId")
    @Mapping(target = "capturedAt", source = "version.capturedAt")
    @Mapping(target = "lineCount", source = "version.lineCount")
    PlanVersionSummaryResponse toVersionSummaryResponse(PlanVersion version, String sessionId);

    PlanVersionContentResponse toVersionContentResponse(PlanVersion version);

    PlanVersionDiffResponse toVersionDiffResponse(int number, String diff);

    @Mapping(target = "id", source = "feedback.id")
    @Mapping(target = "versionNumber", source = "versionNumber")
    @Mapping(target = "reviewer", source = "reviewer")
    @Mapping(target = "createdAt", source = "feedback.createdAt")
    PlanFeedbackResponse toFeedbackResponse(PlanFeedback feedback, int versionNumber, PlanReferenceResponse reviewer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deliveredAt", ignore = true)
    @Mapping(target = "deliveredSessionId", ignore = true)
    @Mapping(target = "planId", source = "planId")
    @Mapping(target = "versionId", source = "versionId")
    @Mapping(target = "reviewerId", source = "reviewerId")
    @Mapping(target = "decision", source = "request.decision")
    @Mapping(target = "comment", source = "request.comment")
    @Mapping(target = "lineStart", source = "request.lineStart")
    @Mapping(target = "lineEnd", source = "request.lineEnd")
    @Mapping(target = "quote", source = "request.quote")
    PlanFeedback toFeedbackEntity(UUID planId, UUID versionId, UUID reviewerId, PlanFeedbackRequest request);

    @Mapping(target = "path", source = "handoff.path")
    @Mapping(target = "status", source = "handoff.status")
    @Mapping(target = "unitNumber", source = "unitNumber")
    @Mapping(target = "content", source = "handoff.content")
    PlanHandoffResponse toHandoffResponse(SessionHandoffResponse handoff, Integer unitNumber);

    PlanFiltersResponse toFiltersResponse(List<PlanReferenceResponse> repositories, List<PlanReferenceResponse> packs,
            List<PlanReferenceResponse> users);

    /**
     * The feedback as the CLI injects it into a session. The plan gives the kind, path and latest
     * version; the feedback gives everything the reviewer wrote.
     */
    @Mapping(target = "id", source = "feedback.id")
    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "planUrl", source = "planUrl")
    @Mapping(target = "kind", source = "plan.kind")
    @Mapping(target = "path", source = "plan.path")
    @Mapping(target = "versionNumber", source = "versionNumber")
    @Mapping(target = "latestVersionNumber", source = "plan.latestVersionNumber")
    @Mapping(target = "sessionId", source = "sessionId")
    @Mapping(target = "reviewerName", source = "reviewerName")
    @Mapping(target = "decision", source = "feedback.decision")
    @Mapping(target = "comment", source = "feedback.comment")
    @Mapping(target = "lineStart", source = "feedback.lineStart")
    @Mapping(target = "lineEnd", source = "feedback.lineEnd")
    @Mapping(target = "quote", source = "feedback.quote")
    @Mapping(target = "createdAt", source = "feedback.createdAt")
    PlanFeedbackDeliveryResponse toDeliveryResponse(PlanFeedback feedback, Plan plan, String planUrl, int versionNumber,
            String sessionId, String reviewerName);

    PlanFeedbackAckResponse toAckResponse(String sessionId, int acknowledged);
}
