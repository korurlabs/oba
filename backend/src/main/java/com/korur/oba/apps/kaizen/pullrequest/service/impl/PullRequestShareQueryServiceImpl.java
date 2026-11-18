package com.korur.oba.apps.kaizen.pullrequest.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.pullrequest.constant.PullRequestShareConstant;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.GithubPullRequestQueryRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareCreateRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareQueryRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubContributorResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubPullRequestPageResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubPullRequestResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubRepositoryResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestSharePreviewResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kaizen.pullrequest.mapper.PullRequestShareMapper;
import com.korur.oba.apps.kaizen.pullrequest.mapper.PullRequestShareMapperHelper;
import com.korur.oba.apps.kaizen.pullrequest.repository.PullRequestShareRepository;
import com.korur.oba.apps.kaizen.pullrequest.service.PullRequestShareQueryService;
import com.korur.oba.apps.kaizen.pullrequest.specification.PullRequestShareSpecification;
import com.korur.oba.apps.kaizen.pullrequest.validator.PullRequestShareValidator;
import com.korur.oba.connect.github.client.GithubClient;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of pull request share read operations.
 *
 * Share reads run within a read-only transaction. The GitHub reads and the
 * share preview, which reads GitHub too, run outside any transaction, so no
 * database connection is held while GitHub answers, which may take up to its
 * ten-second read timeout; the ticket lookup of a preview opens its own short
 * transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PullRequestShareQueryServiceImpl implements PullRequestShareQueryService {

    private final PullRequestShareRepository pullRequestShareRepository;
    private final PullRequestShareValidator pullRequestShareValidator;
    private final PullRequestShareMapper pullRequestShareMapper;
    private final PullRequestShareMapperHelper pullRequestShareMapperHelper;
    private final GithubClient githubClient;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<GithubRepositoryResponse> listGithubRepositories(User user, UUID organizationId) {
        pullRequestShareValidator.ensureGithubEnabled(organizationId);
        return pullRequestShareMapper.toRepositoryResponses(githubClient.listRepositories());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<GithubContributorResponse> listGithubContributors(User user, UUID organizationId,
                                                                  long githubRepositoryId) {
        pullRequestShareValidator.ensureGithubEnabled(organizationId);
        GithubRepositoryPayload repository = pullRequestShareValidator.ensureRepositoryExists(githubRepositoryId);
        return pullRequestShareMapper.toContributorResponses(githubClient.listContributors(repository.fullName()));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GithubPullRequestPageResponse listGithubPullRequests(User user, UUID organizationId,
                                                                long githubRepositoryId,
                                                                GithubPullRequestQueryRequest request) {
        pullRequestShareValidator.ensureGithubEnabled(organizationId);
        pullRequestShareValidator.ensurePickerQueryValid(request.getSearch(), request.getAuthors(),
                request.getPage());
        GithubRepositoryPayload repository = pullRequestShareValidator.ensureRepositoryExists(githubRepositoryId);
        return pullRequestShareMapper.toPageResponse(
                githubClient.listPullRequests(repository.fullName(), request.getSearch(),
                        pullRequestShareMapper.toPullRequestFilter(request), request.getPage()));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GithubPullRequestResponse getGithubPullRequest(User user, UUID organizationId, long githubRepositoryId,
                                                          int number) {
        pullRequestShareValidator.ensureGithubEnabled(organizationId);
        GithubRepositoryPayload repository = pullRequestShareValidator.ensureRepositoryExists(githubRepositoryId);
        GithubPullRequestPayload pullRequest = pullRequestShareValidator.ensurePullRequestExists(repository, number);
        return pullRequestShareMapperHelper.toPullRequestResponse(pullRequest, organizationId, user.getId());
    }

    @Override
    public PullRequestShareResponse getShare(User user, UUID organizationId, UUID shareId) {
        PullRequestShare share = pullRequestShareValidator.ensureShareExists(organizationId, shareId);
        return pullRequestShareMapperHelper.toResponse(share, user.getId());
    }

    @Override
    public Page<PullRequestShareResponse> listShares(User user, UUID organizationId,
                                                     PullRequestShareQueryRequest request) {

        // Always newest first: the list reads as the channel's timeline, so
        // the request's sort fields are ignored, and an edit does not move a share.
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(),
                Sort.by(Sort.Direction.DESC, PullRequestShareConstant.FIELD_CREATED_AT));
        Page<PullRequestShare> page = pullRequestShareRepository.findAll(
                PullRequestShareSpecification.filterBy(organizationId, user.getId(), request), pageable);
        return pullRequestShareMapperHelper.toResponsePage(page, organizationId, user.getId());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PullRequestSharePreviewResponse previewShare(User user, UUID organizationId,
                                                        PullRequestShareCreateRequest request) {
        // The checks of a create, in its order, so a preview fails exactly
        // where the create would.
        pullRequestShareValidator.ensureWithinLimits(request.note(), request.reviewerIds());
        pullRequestShareValidator.ensureReviewersEligible(organizationId, user, request.reviewerIds());
        pullRequestShareValidator.ensureRelatedTicketsValid(request.relatedTicketIds());
        pullRequestShareMapperHelper.resolveAddedTickets(user.getId(), organizationId, request.relatedTicketIds());
        pullRequestShareValidator.ensureGithubEnabled(organizationId);
        GithubRepositoryPayload repository = pullRequestShareValidator.ensureRepositoryExists(
                request.githubRepositoryId());
        GithubPullRequestPayload pullRequest = pullRequestShareValidator.ensurePullRequestExists(repository,
                request.number());
        pullRequestShareValidator.ensurePullRequestChannelExists(organizationId);

        PullRequestShare share = pullRequestShareMapper.toEntity(request, organizationId, repository, pullRequest);
        return pullRequestShareMapperHelper.toPreviewResponse(share, pullRequest, user.getId());
    }

    @Override
    public Map<UUID, PullRequestShareResponse> getSharesByIds(User reader, UUID organizationId,
                                                              Collection<UUID> shareIds) {
        if (shareIds == null || shareIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> requestedIds = new LinkedHashSet<>(shareIds);
        List<PullRequestShare> shares = pullRequestShareRepository.findAllByIdInAndOrganizationId(
                requestedIds, organizationId);

        Map<UUID, PullRequestShareResponse> responsesById = new HashMap<>();
        for (PullRequestShareResponse response : pullRequestShareMapperHelper.toResponses(shares,
                organizationId, reader == null ? null : reader.getId())) {
            responsesById.put(response.id(), response);
        }
        Map<UUID, PullRequestShareResponse> ordered = new LinkedHashMap<>();
        for (UUID shareId : requestedIds) {
            PullRequestShareResponse response = responsesById.get(shareId);
            if (response != null) {
                ordered.put(shareId, response);
            }
        }
        return ordered;
    }

    @Override
    public Optional<PullRequestShare> findShare(UUID organizationId, UUID shareId) {
        return pullRequestShareRepository.findByIdAndOrganizationId(shareId, organizationId);
    }
}
