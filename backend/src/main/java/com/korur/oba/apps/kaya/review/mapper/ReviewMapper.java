package com.korur.oba.apps.kaya.review.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.dto.response.ReleaseResponse;
import com.korur.oba.apps.kaya.review.dto.request.ReviewRequest;
import com.korur.oba.apps.kaya.review.dto.response.ChangeDetailResponse;
import com.korur.oba.apps.kaya.review.dto.response.ChangeResponse;
import com.korur.oba.apps.kaya.review.dto.response.FileHistoryResponse;
import com.korur.oba.apps.kaya.review.dto.response.ReviewResponse;
import com.korur.oba.apps.kaya.review.dto.response.ReviewResultResponse;
import com.korur.oba.apps.kaya.review.entity.Review;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.version.dto.embedded.FileHistoryEntry;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Maps {@link Review}s and the changes list. A change is a version with the role its approval
 * needs and its reviews, which the caller loads for a whole page at once.
 */
@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "versionId", source = "versionId")
    @Mapping(target = "reviewerId", source = "reviewerId")
    @Mapping(target = "decision", source = "request.decision")
    @Mapping(target = "comment", source = "request.comment")
    Review toEntity(UUID versionId, UUID reviewerId, ReviewRequest request);

    ReviewResponse toResponse(Review review);

    List<ReviewResponse> toResponses(List<Review> reviews);

    @Mapping(target = "version", source = "version")
    @Mapping(target = "requiredRole", source = "requiredRole")
    @Mapping(target = "reviews", source = "reviews")
    ChangeResponse toChangeResponse(VersionResponse version, OrganizationMemberRole requiredRole, List<ReviewResponse> reviews);

    @Mapping(target = "version", source = "version")
    @Mapping(target = "requiredRole", source = "requiredRole")
    @Mapping(target = "reviews", source = "reviews")
    @Mapping(target = "violations", source = "violations")
    @Mapping(target = "canApprove", source = "canApprove")
    @Mapping(target = "canReview", source = "canReview")
    ChangeDetailResponse toDetailResponse(VersionResponse version, OrganizationMemberRole requiredRole,
                                          List<ReviewResponse> reviews, List<Violation> violations,
                                          Boolean canApprove, Boolean canReview);

    @Mapping(target = "versionId", source = "entry.versionId")
    @Mapping(target = "number", source = "entry.number")
    @Mapping(target = "status", source = "entry.status")
    @Mapping(target = "source", source = "entry.source")
    @Mapping(target = "change", source = "entry.change")
    @Mapping(target = "sha256", source = "entry.sha256")
    @Mapping(target = "current", source = "entry.current")
    @Mapping(target = "proposedBy", source = "entry.proposedBy")
    @Mapping(target = "reason", source = "entry.reason")
    @Mapping(target = "submittedAt", source = "entry.submittedAt")
    @Mapping(target = "decidedAt", source = "entry.decidedAt")
    @Mapping(target = "reviews", source = "reviews")
    FileHistoryResponse toHistoryResponse(FileHistoryEntry entry, List<ReviewResponse> reviews);

    @Mapping(target = "review", source = "review")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "releases", source = "releases")
    ReviewResultResponse toResultResponse(ReviewResponse review, VersionResponse version, List<ReleaseResponse> releases);
}
