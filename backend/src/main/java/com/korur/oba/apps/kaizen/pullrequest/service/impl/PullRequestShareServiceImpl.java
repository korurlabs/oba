package com.korur.oba.apps.kaizen.pullrequest.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.service.ChatMessageService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.pullrequest.constant.PullRequestShareConstant;
import com.korur.oba.apps.kaizen.pullrequest.constant.PullRequestShareExceptionLogConstant;
import com.korur.oba.apps.kaizen.pullrequest.constant.service.PullRequestShareServiceLogConstant;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareCreateRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareUpdateRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kaizen.pullrequest.mapper.PullRequestShareMapper;
import com.korur.oba.apps.kaizen.pullrequest.mapper.PullRequestShareMapperHelper;
import com.korur.oba.apps.kaizen.pullrequest.repository.PullRequestShareRepository;
import com.korur.oba.apps.kaizen.pullrequest.service.PullRequestShareService;
import com.korur.oba.apps.kaizen.pullrequest.validator.PullRequestShareValidator;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Implementation of pull request share write operations.
 *
 * A share and its card in the pull requests channel are written in one
 * transaction. The card is posted through the chat's ordinary send, with its
 * checks, so a refused send, such as one over the send rate, rolls the new
 * share back. Edit, delete and approval first load the share within the
 * organization (404 otherwise) and then check the author or reviewer (403),
 * so a share the caller cannot see is never confirmed to exist.
 *
 * A create reads GitHub first and opens its transaction only afterwards,
 * from a template rather than under the annotation, so no database
 * connection is held while GitHub answers.
 */
@Service
@RequiredArgsConstructor
public class PullRequestShareServiceImpl implements PullRequestShareService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PullRequestShareServiceImpl.class);

    private final PullRequestShareRepository pullRequestShareRepository;
    private final PullRequestShareValidator pullRequestShareValidator;
    private final PullRequestShareMapper pullRequestShareMapper;
    private final PullRequestShareMapperHelper pullRequestShareMapperHelper;
    private final UserQueryService userQueryService;
    private final ChatMessageService chatMessageService;
    private final TransactionTemplate transactionTemplate;
    private final LoggingService loggingService;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PullRequestShareResponse createShare(User user, UUID organizationId,
                                                PullRequestShareCreateRequest request) {
        pullRequestShareValidator.ensureWithinLimits(request.note(), request.reviewerIds());
        pullRequestShareValidator.ensureReviewersEligible(organizationId, user, request.reviewerIds());
        pullRequestShareValidator.ensureRelatedTicketsValid(request.relatedTicketIds());
        List<Ticket> relatedTickets = pullRequestShareMapperHelper.resolveRelatedTickets(user.getId(),
                organizationId, request.relatedTicketIds());
        pullRequestShareValidator.ensureGithubEnabled(organizationId);
        GithubRepositoryPayload repository = pullRequestShareValidator.ensureRepositoryExists(
                request.githubRepositoryId());
        GithubPullRequestPayload pullRequest = pullRequestShareValidator.ensurePullRequestExists(repository,
                request.number());

        return transactionTemplate.execute((TransactionStatus status) -> {
            UUID channelId = pullRequestShareValidator.ensurePullRequestChannelExists(organizationId);
            PullRequestShare share = pullRequestShareMapper.toEntity(request, organizationId, repository,
                    pullRequest);
            pullRequestShareMapperHelper.setCreateRelations(share, user,
                    resolveReviewers(organizationId, request.reviewerIds()), relatedTickets);
            // Flushed at once: the chat send checks the stored share before
            // its entry may stand for it.
            share = pullRequestShareRepository.saveAndFlush(share);

            chatMessageService.postMessage(user, organizationId,
                    pullRequestShareMapper.toChatMessageCreateRequest(share, channelId), List.of());

            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    PullRequestShareServiceLogConstant.PULL_REQUEST_SHARE_CREATED, share.getId(),
                    share.getRepositoryFullName(), share.getNumber(), share.getReviewers().size(),
                    share.getRelatedTickets().size());
            return pullRequestShareMapperHelper.toResponse(share, user.getId());
        });
    }

    @Override
    @Transactional
    public PullRequestShareResponse editShare(User user, UUID organizationId, UUID shareId,
                                              PullRequestShareUpdateRequest request) {
        PullRequestShare share = pullRequestShareValidator.ensureShareExists(organizationId, shareId);
        pullRequestShareValidator.ensureAuthor(share, user);
        pullRequestShareValidator.ensureWithinLimits(request.note(), request.reviewerIds());
        pullRequestShareValidator.ensureReviewersEligible(organizationId, user, request.reviewerIds());
        pullRequestShareValidator.ensureRelatedTicketsValid(request.relatedTicketIds());
        Map<UUID, Ticket> addedTickets = pullRequestShareMapperHelper.resolveAddedTickets(user.getId(), organizationId,
                pullRequestShareMapperHelper.findAddedTicketIds(share, request.relatedTicketIds()));

        Set<UUID> reviewersBefore = pullRequestShareMapperHelper.getReviewerIds(share);
        pullRequestShareMapper.updateEntity(share, request);
        pullRequestShareMapperHelper.setUpdateRelations(share,
                resolveReviewers(organizationId, request.reviewerIds()), request.relatedTicketIds(), addedTickets);
        share.setEditedAt(Instant.now());
        share = pullRequestShareRepository.saveAndFlush(share);

        chatMessageService.touchPullRequestShareMessage(share, reviewersBefore, true);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                PullRequestShareServiceLogConstant.PULL_REQUEST_SHARE_EDITED, shareId, share.getStatus(),
                share.getReviewers().size(), share.getRelatedTickets().size());
        return pullRequestShareMapperHelper.toResponse(share, user.getId());
    }

    @Override
    @Transactional
    public void deleteShare(User user, UUID organizationId, UUID shareId) {
        PullRequestShare share = pullRequestShareValidator.ensureShareExists(organizationId, shareId);
        pullRequestShareValidator.ensureAuthor(share, user);

        // Marks only the share as deleted. A JPA delete would cascade to the
        // reviewer rows, which are not soft-deletable, and lose the approvals.
        pullRequestShareRepository.deleteByIdWithoutRelations(share.getId());
        chatMessageService.removePullRequestShareMessage(share);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                PullRequestShareServiceLogConstant.PULL_REQUEST_SHARE_DELETED, shareId);
    }

    @Override
    @Transactional
    public PullRequestShareResponse setApproval(User user, UUID organizationId, UUID shareId, boolean approved) {
        PullRequestShare share = pullRequestShareValidator.ensureShareExists(organizationId, shareId);
        PullRequestShareReviewer reviewer = pullRequestShareValidator.ensureReviewer(share, user);

        if (approved == (reviewer.getApprovedAt() != null)) {
            return pullRequestShareMapperHelper.toResponse(share, user.getId());
        }
        // The reviewer row is the managed row of the share's own collection,
        // so the response below already carries the change.
        reviewer.setApprovedAt(approved ? Instant.now() : null);
        chatMessageService.touchPullRequestShareMessage(share, pullRequestShareMapperHelper.getReviewerIds(share),
                false);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                PullRequestShareServiceLogConstant.PULL_REQUEST_SHARE_APPROVAL_SET, shareId, user.getId(), approved);
        return pullRequestShareMapperHelper.toResponse(share, user.getId());
    }

    /**
     * Loads the checked reviewers in the request's order, which is their
     * order on the card. A reviewer whose account is gone since the
     * eligibility check is refused as an ineligible one.
     */
    private List<User> resolveReviewers(UUID organizationId, List<UUID> reviewerIds) {
        if (reviewerIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> usersById = new HashMap<>();
        for (User found : userQueryService.getUserEntitiesByIds(reviewerIds)) {
            usersById.put(found.getId(), found);
        }
        List<User> reviewers = new ArrayList<>();
        for (UUID reviewerId : reviewerIds) {
            User reviewer = usersById.get(reviewerId);
            if (reviewer == null) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                        PullRequestShareExceptionLogConstant.PULL_REQUEST_SHARE_REVIEWER_NOT_ELIGIBLE,
                        List.of(reviewerId), organizationId);
                throw new ValidationException(
                        ExceptionDomainKaizenError.VAL_PULL_REQUEST_SHARE_INVALID,
                        Map.of(PullRequestShareConstant.FIELD_USER_ID, reviewerId));
            }
            reviewers.add(reviewer);
        }
        return reviewers;
    }
}
