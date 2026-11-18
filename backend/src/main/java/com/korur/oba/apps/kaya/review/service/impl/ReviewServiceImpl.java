package com.korur.oba.apps.kaya.review.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.mapper.ReleaseMapper;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.review.constant.ReviewConstant;
import com.korur.oba.apps.kaya.review.constant.service.ReviewServiceLogConstant;
import com.korur.oba.apps.kaya.review.dto.request.ChangeSubmitRequest;
import com.korur.oba.apps.kaya.review.dto.request.ReviewRequest;
import com.korur.oba.apps.kaya.review.dto.response.ReviewResultResponse;
import com.korur.oba.apps.kaya.review.entity.Review;
import com.korur.oba.apps.kaya.review.enums.ReviewDecision;
import com.korur.oba.apps.kaya.review.helper.ReviewAccessHelper;
import com.korur.oba.apps.kaya.review.mapper.ReviewMapper;
import com.korur.oba.apps.kaya.review.repository.ReviewRepository;
import com.korur.oba.apps.kaya.review.service.ReviewService;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;
import com.korur.oba.apps.kaya.version.mapper.VersionMapper;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.apps.kaya.version.service.VersionService;

/**
 * Implements {@link ReviewService}: checks the reviewer's role and that the proposer never reviews
 * their own version, decides the version through {@link VersionService} and, on approval, releases
 * through {@link ReleaseService} in the same transaction.
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ReviewServiceImpl.class);

    private final OrganizationAccessQueryService organizationAccessQueryService;
    private final VersionService versionService;
    private final VersionQueryService versionQueryService;
    private final ReleaseService releaseService;
    private final ReleaseQueryService releaseQueryService;
    private final ValidationQueryService validationQueryService;
    private final ReviewRepository reviewRepository;
    private final ReviewAccessHelper reviewAccessHelper;
    private final ReviewMapper reviewMapper;
    private final VersionMapper versionMapper;
    private final ReleaseMapper releaseMapper;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public VersionResponse submit(User user, UUID organizationId, UUID versionId, ChangeSubmitRequest request) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.SUBMITTING,
                user.getId(), versionId, organizationId);
        Version submitted = versionService.submit(user, organizationId, versionId, request.reason());
        // Checked after the version checks, in the same transaction: a refusal rolls the submit back,
        // so the draft stays a draft. A file save does not check the composition rules, because the
        // version domain cannot compose repositories; submit and approval are where they are enforced.
        List<Violation> violations = releaseQueryService.validateVersion(organizationId, submitted);
        if (!violations.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.SUBMIT_REFUSED,
                    versionId, violations.size());
        }
        validationQueryService.requireValid(violations);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.SUBMITTED,
                versionId, submitted.getPackId(), submitted.getExecutableChange());
        return versionMapper.toResponse(submitted);
    }

    @Override
    @Transactional
    public ReviewResultResponse review(User user, UUID organizationId, UUID versionId, ReviewRequest request) {
        OrganizationMemberRole role = organizationAccessQueryService.verifyTenantAccess(user, organizationId).getRole();
        Version version = versionQueryService.getEntityById(organizationId, versionId);
        requireInReview(user, version);
        if (request.decision() != ReviewDecision.APPROVE && request.comment() == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.REVIEW_COMMENT_MISSING,
                    request.decision(), versionId, user.getId());
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(ReviewConstant.FIELD_COMMENT, ReviewConstant.COMMENT_REQUIRED_MESSAGE));
        }
        boolean approve = request.decision() == ReviewDecision.APPROVE;
        requireAllowed(user, role, version, approve);

        // The version domain locks the version and its owner and checks the status again under
        // the lock, so two reviewers deciding at once cannot both win.
        Version after = version;
        List<Release> releases = List.of();
        if (approve) {
            after = versionService.approve(organizationId, versionId);
            releases = releaseService.releaseVersion(organizationId, after);
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.VERSION_APPROVED,
                    versionId, after.getPackId(), releases.size());
        } else if (request.decision() == ReviewDecision.REJECT) {
            after = versionService.reject(organizationId, versionId);
        }

        Review review = reviewRepository.save(reviewMapper.toEntity(versionId, user.getId(), request));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.REVIEW_RECORDED,
                user.getId(), request.decision(), versionId, after.getPackId());
        return reviewMapper.toResultResponse(reviewMapper.toResponse(review), versionMapper.toResponse(after),
                releases.stream().map(releaseMapper::toResponse).toList());
    }

    private void requireInReview(User user, Version version) {
        if (version.getStatus() == VersionStatus.IN_REVIEW) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.REVIEW_NOT_IN_REVIEW,
                version.getId(), version.getStatus(), user.getId());
        throw new ConflictException(ExceptionDomainKayaError.REVIEW_VERSION_NOT_IN_REVIEW,
                Map.of(ReviewConstant.FIELD_VERSION_ID, version.getId().toString(),
                        ReviewConstant.FIELD_STATUS, version.getStatus().name()));
    }

    private void requireAllowed(User user, OrganizationMemberRole role, Version version, boolean approve) {
        Optional<String> refusal = reviewAccessHelper.refusal(user.getId(), role, version, approve);
        if (refusal.isEmpty()) {
            return;
        }
        OrganizationMemberRole required = approve
                ? reviewAccessHelper.requiredRole(version.getExecutableChange())
                : OrganizationMemberRole.ADMIN;
        if (refusal.get().equals(ReviewConstant.REASON_PROPOSER)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.REVIEW_BY_PROPOSER,
                    user.getId(), version.getId());
        } else {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReviewServiceLogConstant.REVIEW_ROLE_TOO_LOW,
                    user.getId(), role, version.getId(), required);
        }
        throw new ForbiddenException(ExceptionDomainKayaError.REVIEW_NOT_ALLOWED,
                Map.of(ReviewConstant.FIELD_VERSION_ID, version.getId().toString(),
                        ReviewConstant.FIELD_REASON, refusal.get(),
                        ReviewConstant.FIELD_REQUIRED_ROLE, required.name()));
    }
}
