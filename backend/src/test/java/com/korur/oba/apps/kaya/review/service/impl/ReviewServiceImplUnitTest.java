package com.korur.oba.apps.kaya.review.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.mapper.ReleaseMapper;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.review.dto.request.ChangeSubmitRequest;
import com.korur.oba.apps.kaya.review.dto.request.ReviewRequest;
import com.korur.oba.apps.kaya.review.entity.Review;
import com.korur.oba.apps.kaya.review.enums.ReviewDecision;
import com.korur.oba.apps.kaya.review.helper.ReviewAccessHelper;
import com.korur.oba.apps.kaya.review.mapper.ReviewMapper;
import com.korur.oba.apps.kaya.review.repository.ReviewRepository;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionSource;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;
import com.korur.oba.apps.kaya.version.mapper.VersionMapper;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.apps.kaya.version.service.VersionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceImplUnitTest {

    @Mock
    private OrganizationAccessQueryService organizationAccessQueryService;
    @Mock
    private VersionService versionService;
    @Mock
    private VersionQueryService versionQueryService;
    @Mock
    private ReleaseService releaseService;
    @Mock
    private ReleaseQueryService releaseQueryService;
    @Mock
    private ValidationQueryService validationQueryService;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private VersionMapper versionMapper;
    @Mock
    private ReleaseMapper releaseMapper;
    @Mock
    private LoggingService loggingService;

    private ReviewServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID proposerId = UUID.randomUUID();
    private User reviewer;
    private Version version;

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(organizationAccessQueryService, versionService, versionQueryService,
                releaseService, releaseQueryService, validationQueryService, reviewRepository, new ReviewAccessHelper(),
                reviewMapper, versionMapper, releaseMapper, loggingService);
        reviewer = User.builder().id(UUID.randomUUID()).build();
        version = Version.builder().id(versionId).organizationId(organizationId).packId(UUID.randomUUID())
                .proposedBy(proposerId).status(VersionStatus.IN_REVIEW).source(VersionSource.MANUAL)
                .executableChange(false).build();
        when(versionQueryService.getEntityById(organizationId, versionId)).thenReturn(version);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        role(OrganizationMemberRole.ADMIN);
    }

    @Test
    void submit_compositionsValid_returnsVersionInReview() {
        User proposer = User.builder().id(proposerId).build();
        when(versionService.submit(proposer, organizationId, versionId, "Allow git status everywhere")).thenReturn(version);
        when(releaseQueryService.validateVersion(organizationId, version)).thenReturn(List.of());

        service.submit(proposer, organizationId, versionId, new ChangeSubmitRequest("Allow git status everywhere"));

        InOrder order = inOrder(versionService, releaseQueryService, validationQueryService);
        order.verify(versionService).submit(proposer, organizationId, versionId, "Allow git status everywhere");
        order.verify(releaseQueryService).validateVersion(organizationId, version);
        order.verify(validationQueryService).requireValid(List.of());
        verify(versionMapper).toResponse(version);
    }

    @Test
    void submit_brokenComposition_refusedWithTheViolations() {
        User proposer = User.builder().id(proposerId).build();
        List<Violation> violations = List.of(new Violation("AGENTS.md", ViolationRule.SYMLINK_DANGLING, "dangling",
                UUID.randomUUID(), null));
        when(versionService.submit(any(), any(), any(), any())).thenReturn(version);
        when(releaseQueryService.validateVersion(organizationId, version)).thenReturn(violations);
        doThrow(new ValidationException(ExceptionDomainKayaError.VALIDATION_RULES_BROKEN, Map.of()))
                .when(validationQueryService).requireValid(violations);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> service.submit(proposer, organizationId, versionId, new ChangeSubmitRequest("Add a long rule")));

        assertEquals("KAY_CFG_001", exception.getErrorCode());
        verify(versionMapper, never()).toResponse(any(Version.class));
    }

    @Test
    void review_approve_approvesThenReleasesEveryComposingRepository() {
        Version approved = Version.builder().id(versionId).packId(version.getPackId()).status(VersionStatus.APPROVED).build();
        Release release = Release.builder().id(UUID.randomUUID()).build();
        when(versionService.approve(organizationId, versionId)).thenReturn(approved);
        when(releaseService.releaseVersion(organizationId, approved)).thenReturn(List.of(release, release));

        service.review(reviewer, organizationId, versionId, new ReviewRequest(ReviewDecision.APPROVE, null));

        InOrder order = inOrder(versionService, releaseService, reviewRepository);
        order.verify(versionService).approve(organizationId, versionId);
        order.verify(releaseService).releaseVersion(organizationId, approved);
        order.verify(reviewRepository).save(any());
        verify(reviewMapper).toResultResponse(any(), any(), anyList());
        verify(releaseMapper, times(2)).toResponse(release);
    }

    @Test
    void review_reject_handsBackWithoutRelease() {
        when(versionService.reject(organizationId, versionId)).thenReturn(version);

        service.review(reviewer, organizationId, versionId, new ReviewRequest(ReviewDecision.REJECT, "Too broad"));

        verify(versionService).reject(organizationId, versionId);
        verify(releaseService, never()).releaseVersion(any(), any());
    }

    @Test
    void review_comment_changesOnlyTheReviews() {
        service.review(reviewer, organizationId, versionId, new ReviewRequest(ReviewDecision.COMMENT, "Why this rule?"));

        verify(reviewRepository).save(any());
        verify(versionService, never()).approve(any(), any());
        verify(versionService, never()).reject(any(), any());
    }

    @Test
    void review_rejectWithoutComment_refusedBeforeAnyWrite() {
        assertThrows(ValidationException.class,
                () -> service.review(reviewer, organizationId, versionId, new ReviewRequest(ReviewDecision.REJECT, null)));

        verify(versionService, never()).reject(any(), any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void review_byProposer_refusedEvenAsOwner() {
        role(OrganizationMemberRole.OWNER);
        User proposer = User.builder().id(proposerId).build();

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> service.review(proposer, organizationId, versionId, new ReviewRequest(ReviewDecision.APPROVE, null)));

        assertEquals("KAY_CFG_004", exception.getErrorCode());
        verify(versionService, never()).approve(any(), any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void review_adminApprovesExecutableChange_refused() {
        version.setExecutableChange(true);

        assertThrows(ForbiddenException.class,
                () -> service.review(reviewer, organizationId, versionId, new ReviewRequest(ReviewDecision.APPROVE, null)));

        verify(versionService, never()).approve(any(), any());
    }

    @Test
    void review_ownerApprovesExecutableChange_allowed() {
        version.setExecutableChange(true);
        role(OrganizationMemberRole.OWNER);
        when(versionService.approve(organizationId, versionId)).thenReturn(version);
        when(releaseService.releaseVersion(organizationId, version)).thenReturn(List.of());

        service.review(reviewer, organizationId, versionId, new ReviewRequest(ReviewDecision.APPROVE, null));

        verify(releaseService).releaseVersion(organizationId, version);
    }

    @Test
    void review_versionNotInReview_conflict() {
        version.setStatus(VersionStatus.DRAFT);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.review(reviewer, organizationId, versionId, new ReviewRequest(ReviewDecision.APPROVE, null)));

        assertEquals("KAY_CFG_017", exception.getErrorCode());
        verify(versionService, never()).approve(any(), any());
    }

    private void role(OrganizationMemberRole role) {
        when(organizationAccessQueryService.verifyTenantAccess(any(), any()))
                .thenReturn(OrganizationMember.builder().role(role).build());
    }
}
