package com.korur.oba.apps.kaya.review.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.review.constant.service.ReviewServiceLogConstant;
import com.korur.oba.apps.kaya.review.dto.request.ChangeQueryRequest;
import com.korur.oba.apps.kaya.review.dto.response.ChangeDetailResponse;
import com.korur.oba.apps.kaya.review.dto.response.ChangeResponse;
import com.korur.oba.apps.kaya.review.dto.response.FileHistoryResponse;
import com.korur.oba.apps.kaya.review.dto.response.ReviewResponse;
import com.korur.oba.apps.kaya.review.entity.Review;
import com.korur.oba.apps.kaya.review.helper.ReviewAccessHelper;
import com.korur.oba.apps.kaya.review.mapper.ReviewMapper;
import com.korur.oba.apps.kaya.review.repository.ReviewRepository;
import com.korur.oba.apps.kaya.review.service.ReviewQueryService;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.version.dto.embedded.FileHistoryEntry;
import com.korur.oba.apps.kaya.version.dto.request.VersionQueryRequest;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionOrder;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;
import com.korur.oba.apps.kaya.version.mapper.VersionMapper;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;

/**
 * Implements {@link ReviewQueryService}: lists changes and their reviews and checks a version against
 * every repository that composes it through {@link ReleaseQueryService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryServiceImpl implements ReviewQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ReviewQueryServiceImpl.class);

    private final OrganizationAccessQueryService organizationAccessQueryService;
    private final VersionQueryService versionQueryService;
    private final ReleaseQueryService releaseQueryService;
    private final ReviewRepository reviewRepository;
    private final ReviewAccessHelper reviewAccessHelper;
    private final ReviewMapper reviewMapper;
    private final VersionMapper versionMapper;
    private final LoggingService loggingService;

    @Override
    public Page<ChangeResponse> getChanges(UUID organizationId, ChangeQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.LISTING_CHANGES,
                organizationId, request.getPackId(), request.getStatus());
        VersionQueryRequest filter = new VersionQueryRequest();
        filter.setPage(request.getPage());
        filter.setAmount(request.getAmount());
        filter.setPackId(request.getPackId());
        filter.setStatus(request.getStatus());
        Page<VersionResponse> versions = versionQueryService.getVersions(organizationId, filter, VersionOrder.RECENTLY_CHANGED);

        // One read of the reviews for the whole page, not one per version.
        List<UUID> versionIds = versions.getContent().stream().map(VersionResponse::id).toList();
        Map<UUID, List<Review>> reviews = versionIds.isEmpty()
                ? Map.of()
                : reviewRepository.findByVersionIdInOrderByCreatedAtAscIdAsc(versionIds).stream()
                        .collect(Collectors.groupingBy(Review::getVersionId));
        return versions.map(version -> reviewMapper.toChangeResponse(version,
                reviewAccessHelper.requiredRole(version.executableChange()),
                reviewMapper.toResponses(reviews.getOrDefault(version.id(), List.of()))));
    }

    @Override
    public ChangeDetailResponse getChange(User user, UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.READING_CHANGE,
                versionId, organizationId, user.getId());
        OrganizationMemberRole role = organizationAccessQueryService.verifyTenantAccess(user, organizationId).getRole();
        Version version = versionQueryService.getEntityById(organizationId, versionId);
        List<ReviewResponse> reviews = reviewMapper.toResponses(
                reviewRepository.findByVersionIdOrderByCreatedAtAscIdAsc(versionId));
        OrganizationMemberRole requiredRole = reviewAccessHelper.requiredRole(version.getExecutableChange());
        VersionResponse response = versionMapper.toResponse(version);
        if (version.getStatus() == VersionStatus.DRAFT && user.getId().equals(version.getProposedBy())) {
            // A submit checks the same compositions, so the proposer sees what it would refuse.
            List<Violation> violations = releaseQueryService.validateVersion(organizationId, version);
            return reviewMapper.toDetailResponse(response, requiredRole, reviews, violations, false, false);
        }
        if (version.getStatus() != VersionStatus.IN_REVIEW) {
            return reviewMapper.toDetailResponse(response, requiredRole, reviews, null, false, false);
        }

        List<Violation> violations = releaseQueryService.validateVersion(organizationId, version);
        boolean canApprove = baseIsCurrent(organizationId, version) && violations.isEmpty()
                && reviewAccessHelper.refusal(user.getId(), role, version, true).isEmpty();
        boolean canReview = reviewAccessHelper.refusal(user.getId(), role, version, false).isEmpty();
        return reviewMapper.toDetailResponse(response, requiredRole, reviews, violations, canApprove, canReview);
    }

    @Override
    public Page<FileHistoryResponse> getFileHistory(User user, UUID organizationId, UUID versionId, String path,
                                                    QueryRequest page) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.READING_HISTORY,
                path, versionId, organizationId, user.getId());
        Page<FileHistoryEntry> entries = versionQueryService.getFileHistory(user, organizationId, versionId, path, page);
        // One read of the reviews for the whole page, not one per version.
        List<UUID> versionIds = entries.getContent().stream().map(FileHistoryEntry::versionId).toList();
        Map<UUID, List<Review>> reviews = versionIds.isEmpty()
                ? Map.of()
                : reviewRepository.findByVersionIdInOrderByCreatedAtAscIdAsc(versionIds).stream()
                        .collect(Collectors.groupingBy(Review::getVersionId));
        return entries.map(entry -> reviewMapper.toHistoryResponse(entry,
                reviewMapper.toResponses(reviews.getOrDefault(entry.versionId(), List.of()))));
    }

    /**
     * Whether the version still rests on its owner's current version, which an approval requires.
     * An owner's current version is its one APPROVED version, since an approval or a seed
     * supersedes the one before; an owner without one has no current version.
     */
    private boolean baseIsCurrent(UUID organizationId, Version version) {
        Version base = version.getBaseVersion();
        if (base != null) {
            return base.getStatus() == VersionStatus.APPROVED;
        }
        VersionQueryRequest approved = new VersionQueryRequest();
        approved.setPackId(version.getPackId());
        approved.setStatus(VersionStatus.APPROVED);
        approved.setAmount(1);
        return versionQueryService.getVersions(organizationId, approved).isEmpty();
    }
}
