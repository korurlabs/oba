package com.korur.oba.apps.kaizen.pullrequest.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.pullrequest.constant.PullRequestShareConstant;
import com.korur.oba.apps.kaizen.pullrequest.constant.PullRequestShareExceptionLogConstant;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kaizen.pullrequest.repository.PullRequestShareRepository;
import com.korur.oba.apps.kaizen.pullrequest.repository.PullRequestShareReviewerRepository;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.connect.github.client.GithubClient;
import com.korur.oba.connect.github.constant.GithubConstant;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Validator for pull request share lookups, authorship, reviewer rights,
 * request limits, reviewer eligibility, related tickets, the pull requests
 * channel, and the GitHub repository and pull request a request names.
 *
 * The request DTOs carry the size limits as Bean Validation annotations; the
 * limit checks here guard callers that build a request without passing it
 * through the controller, so a service never stores an oversized share or
 * sends GitHub an oversized search or a malformed author. Every refusal of a limit, a reviewer or a
 * repeated related ticket answers
 * {@link ExceptionDomainKaizenError#VAL_PULL_REQUEST_SHARE_INVALID}, naming
 * the field.
 *
 * Every refusal here answers a request the client got wrong or may not make,
 * an expected 4xx and no server fault, so it is logged at warn, never at
 * error, and error alerts keep pointing at real failures. GitHub's own
 * failures are logged at error by the GitHub client.
 *
 * A share of another organization is reported as not found, so its existence
 * is not revealed, and so is a related ticket that is deleted or on a board
 * the author is not a member of. The GitHub repository is resolved against the
 * installation's own list, so no request reaches a repository the
 * installation was not given.
 */
@Component
@RequiredArgsConstructor
public class PullRequestShareValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PullRequestShareValidator.class);

    private final PullRequestShareRepository pullRequestShareRepository;
    private final PullRequestShareReviewerRepository pullRequestShareReviewerRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessValidator appAccessValidator;
    private final ChatChannelQueryService chatChannelQueryService;
    private final GithubClient githubClient;
    private final LoggingService loggingService;

    /**
     * Ensures a live share of the organization exists and returns it.
     *
     * @param organizationId organization the caller works in
     * @param shareId        ID of the share
     * @return the share
     * @throws NotFoundException NOT_PULL_REQUEST_SHARE_FOUND when the share is absent, deleted or of another
     *                           organization
     */
    public PullRequestShare ensureShareExists(UUID organizationId, UUID shareId) {
        return pullRequestShareRepository.findByIdAndOrganizationId(shareId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            PullRequestShareExceptionLogConstant.PULL_REQUEST_SHARE_NOT_FOUND, shareId, organizationId);
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_PULL_REQUEST_SHARE_FOUND,
                            Map.of(PullRequestShareConstant.FIELD_SHARE_ID, shareId));
                });
    }

    /**
     * Ensures the user wrote the share; only the author may edit or delete it.
     *
     * @param share the share
     * @param user  the current user
     * @throws ForbiddenException SEC_CANNOT_EDIT_OTHERS_SHARE when the user is not the author
     */
    public void ensureAuthor(PullRequestShare share, User user) {
        if (!share.getAuthor().getId().equals(user.getId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    PullRequestShareExceptionLogConstant.PULL_REQUEST_SHARE_NOT_AUTHOR, share.getId(), user.getId());
            throw new ForbiddenException(
                    ExceptionDomainKaizenError.SEC_CANNOT_EDIT_OTHERS_SHARE,
                    Map.of(PullRequestShareConstant.FIELD_SHARE_ID, share.getId()));
        }
    }

    /**
     * Ensures the user is a reviewer of the share and returns their row; only a
     * reviewer may approve the share or withdraw the approval.
     *
     * @param share the share
     * @param user  the current user
     * @return the user's reviewer row
     * @throws ForbiddenException SEC_NOT_PULL_REQUEST_REVIEWER when the user is not a reviewer
     */
    public PullRequestShareReviewer ensureReviewer(PullRequestShare share, User user) {
        return pullRequestShareReviewerRepository.findByShareIdAndUserId(share.getId(), user.getId())
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            PullRequestShareExceptionLogConstant.PULL_REQUEST_SHARE_NOT_REVIEWER,
                            share.getId(), user.getId());
                    return new ForbiddenException(
                            ExceptionDomainKaizenError.SEC_NOT_PULL_REQUEST_REVIEWER,
                            Map.of(PullRequestShareConstant.FIELD_SHARE_ID, share.getId()));
                });
    }

    /**
     * Ensures a create or edit request stays within the share's limits: a note
     * of at most MAX_NOTE_LENGTH characters and a reviewer list of at most
     * MAX_REVIEWERS IDs, none of them null.
     *
     * @param note        the note, nullable
     * @param reviewerIds the reviewer IDs
     * @throws ValidationException VAL_PULL_REQUEST_SHARE_INVALID naming the first field that breaks a limit
     */
    public void ensureWithinLimits(String note, List<UUID> reviewerIds) {
        if (note != null && note.length() > PullRequestShareConstant.MAX_NOTE_LENGTH) {
            throw invalid(PullRequestShareConstant.FIELD_NOTE, PullRequestShareConstant.MAX_NOTE_LENGTH);
        }
        if (reviewerIds == null
                || reviewerIds.size() > PullRequestShareConstant.MAX_REVIEWERS
                || reviewerIds.stream().anyMatch(Objects::isNull)) {
            throw invalid(PullRequestShareConstant.FIELD_REVIEWER_IDS, PullRequestShareConstant.MAX_REVIEWERS);
        }
    }

    /**
     * Ensures every reviewer may review the author's share: named once, not
     * the author, and an ACTIVE owner, admin or member of the organization
     * with an ACTIVE account who may open Kaizen there. GUESTs take no part in
     * reviews.
     *
     * @param organizationId the share's organization
     * @param author         the share's author
     * @param reviewerIds    the reviewer IDs, within the limits
     * @throws ValidationException VAL_PULL_REQUEST_SHARE_INVALID naming the reviewer field, or
     *         APP_VAL_002 naming the first reviewer who may not open Kaizen
     */
    public void ensureReviewersEligible(UUID organizationId, User author, List<UUID> reviewerIds) {
        Set<UUID> seen = new HashSet<>();
        for (UUID reviewerId : reviewerIds) {
            if (!seen.add(reviewerId)) {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                        PullRequestShareExceptionLogConstant.PULL_REQUEST_SHARE_DUPLICATE_REVIEWER, reviewerId);
                throw reviewerRefused(reviewerId);
            }
            if (reviewerId.equals(author.getId())) {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                        PullRequestShareExceptionLogConstant.PULL_REQUEST_SHARE_AUTHOR_AS_REVIEWER, reviewerId);
                throw reviewerRefused(reviewerId);
            }
        }
        if (reviewerIds.isEmpty()) {
            return;
        }
        Set<UUID> eligibleIds = organizationMemberQueryService.findActiveUserIdsByRoles(
                organizationId, PullRequestShareConstant.REVIEWER_ROLES);
        List<UUID> refusedIds = reviewerIds.stream()
                .filter(reviewerId -> !eligibleIds.contains(reviewerId))
                .toList();
        if (!refusedIds.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    PullRequestShareExceptionLogConstant.PULL_REQUEST_SHARE_REVIEWER_NOT_ELIGIBLE,
                    refusedIds, organizationId);
            throw reviewerRefused(refusedIds.getFirst());
        }
        appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.KAIZEN, reviewerIds);
    }

    /**
     * Ensures a create or edit request names at most MAX_RELATED_TICKETS
     * related tickets, none of them null and each once. A ticket is related to
     * a share at most once, and an edit matches stored rows by ticket.
     *
     * @param relatedTicketIds the related ticket IDs in display order
     * @throws ValidationException {@link ExceptionDomainKaizenError#VAL_PULL_REQUEST_SHARE_INVALID}
     *         naming the related ticket field
     */
    public void ensureRelatedTicketsValid(List<UUID> relatedTicketIds) {
        if (relatedTicketIds == null
                || relatedTicketIds.size() > PullRequestShareConstant.MAX_RELATED_TICKETS
                || relatedTicketIds.stream().anyMatch(Objects::isNull)) {
            throw invalid(PullRequestShareConstant.FIELD_RELATED_TICKET_IDS,
                    PullRequestShareConstant.MAX_RELATED_TICKETS);
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID ticketId : relatedTicketIds) {
            if (!seen.add(ticketId)) {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                        PullRequestShareExceptionLogConstant.PULL_REQUEST_SHARE_DUPLICATE_RELATED_TICKET, ticketId);
                throw new ValidationException(
                        ExceptionDomainKaizenError.VAL_PULL_REQUEST_SHARE_INVALID,
                        Map.of(PullRequestShareConstant.FIELD_RELATED_TICKET_IDS, ticketId));
            }
        }
    }

    /**
     * Ensures every related ticket being added was found among the tickets the
     * author can open. The tickets come from
     * {@link com.korur.oba.apps.kaizen.ticket.service.TicketQueryService#getAccessibleTickets},
     * which leaves out deleted tickets and tickets on boards the author is not
     * a member of; both are reported as not found, so a foreign ticket is not
     * revealed.
     *
     * @param requestedTicketIds IDs of the tickets being added
     * @param foundTickets       tickets returned for those IDs
     * @throws NotFoundException {@link ExceptionDomainKaizenError#NOT_TICKET_FOUND} naming the first
     *         missing ticket
     */
    public void ensureRelatedTicketsAccessible(Collection<UUID> requestedTicketIds, Collection<Ticket> foundTickets) {
        Set<UUID> foundIds = new HashSet<>();
        for (Ticket ticket : foundTickets) {
            foundIds.add(ticket.getId());
        }
        List<UUID> missingIds = requestedTicketIds.stream()
                .filter(ticketId -> !foundIds.contains(ticketId))
                .toList();
        if (!missingIds.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    PullRequestShareExceptionLogConstant.PULL_REQUEST_SHARE_RELATED_TICKETS_NOT_ACCESSIBLE,
                    missingIds);
            throw new NotFoundException(
                    ExceptionDomainKaizenError.NOT_TICKET_FOUND,
                    Map.of(PullRequestShareConstant.FIELD_TICKET_ID, missingIds.getFirst()));
        }
    }

    /**
     * Ensures the picker's search, authors and page are within their limits: a
     * search of at most MAX_SEARCH_LENGTH characters, at most MAX_AUTHOR_FILTERS
     * authors that are all GitHub logins, and a page of at least MIN_PAGE. A
     * login becomes part of a GitHub search query, where any other character
     * could add a qualifier.
     *
     * @param search  the search text, nullable
     * @param authors the author logins, nullable; a null entry narrows nothing
     * @param page    the page number
     * @throws ValidationException VAL_PULL_REQUEST_SHARE_INVALID naming the field
     */
    public void ensurePickerQueryValid(String search, List<String> authors, int page) {
        if (search != null && search.length() > PullRequestShareConstant.MAX_SEARCH_LENGTH) {
            throw invalid(PullRequestShareConstant.FIELD_SEARCH, PullRequestShareConstant.MAX_SEARCH_LENGTH);
        }
        if (authors != null && !areAuthorsValid(authors)) {
            throw invalid(PullRequestShareConstant.FIELD_AUTHORS, PullRequestShareConstant.MAX_AUTHOR_FILTERS);
        }
        if (page < PullRequestShareConstant.MIN_PAGE) {
            throw invalid(PullRequestShareConstant.FIELD_PAGE, PullRequestShareConstant.MIN_PAGE);
        }
    }

    /**
     * Ensures the organization may use the GitHub integration: every key is
     * set and the organization is the configured one.
     *
     * @param organizationId the request's organization
     * @throws NotFoundException NOT_GITHUB_NOT_CONFIGURED otherwise
     */
    public void ensureGithubEnabled(UUID organizationId) {
        if (!githubClient.isEnabledFor(organizationId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    PullRequestShareExceptionLogConstant.GITHUB_NOT_CONFIGURED, organizationId);
            throw new NotFoundException(
                    ExceptionDomainKaizenError.NOT_GITHUB_NOT_CONFIGURED,
                    Map.of(PullRequestShareConstant.FIELD_ORGANIZATION_ID, organizationId));
        }
    }

    /**
     * Ensures the repository is one of the installation's and returns it.
     * Call ensureGithubEnabled first.
     *
     * @param githubRepositoryId GitHub's repository ID, from the request
     * @return the repository as the installation lists it
     * @throws NotFoundException NOT_GITHUB_REPOSITORY_FOUND when the installation cannot read it
     */
    public GithubRepositoryPayload ensureRepositoryExists(long githubRepositoryId) {
        return githubClient.findRepository(githubRepositoryId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            PullRequestShareExceptionLogConstant.GITHUB_REPOSITORY_NOT_FOUND, githubRepositoryId);
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_GITHUB_REPOSITORY_FOUND,
                            Map.of(PullRequestShareConstant.FIELD_GITHUB_REPOSITORY_ID, githubRepositoryId));
                });
    }

    /**
     * Ensures GitHub has the pull request in the repository and returns it.
     *
     * @param repository the installation's repository
     * @param number     the pull request number
     * @return the pull request as GitHub returned it
     * @throws NotFoundException NOT_GITHUB_PULL_REQUEST_FOUND when GitHub has no such pull request
     */
    public GithubPullRequestPayload ensurePullRequestExists(GithubRepositoryPayload repository, int number) {
        return githubClient.getPullRequest(repository.fullName(), number)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            PullRequestShareExceptionLogConstant.GITHUB_PULL_REQUEST_NOT_FOUND,
                            repository.fullName(), number);
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_GITHUB_PULL_REQUEST_FOUND,
                            Map.of(PullRequestShareConstant.FIELD_NUMBER, number));
                });
    }

    /**
     * Ensures the organization has a live pull requests channel and returns
     * its ID, so a share is never saved without the card that shows it.
     *
     * @param organizationId the share's organization
     * @return the channel ID
     * @throws ValidationException VAL_PULL_REQUEST_CHANNEL_ABSENT when no channel has the PULL_REQUESTS purpose
     */
    public UUID ensurePullRequestChannelExists(UUID organizationId) {
        return chatChannelQueryService.findPullRequestChannelId(organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            PullRequestShareExceptionLogConstant.PULL_REQUEST_CHANNEL_ABSENT, organizationId);
                    return new ValidationException(
                            ExceptionDomainKaizenError.VAL_PULL_REQUEST_CHANNEL_ABSENT,
                            Map.of(PullRequestShareConstant.FIELD_ORGANIZATION_ID, organizationId));
                });
    }

    /**
     * Tells whether the picker's authors are few enough and all GitHub logins.
     */
    private boolean areAuthorsValid(List<String> authors) {
        return authors.size() <= PullRequestShareConstant.MAX_AUTHOR_FILTERS
                && authors.stream()
                .filter(Objects::nonNull)
                .allMatch(login -> GithubConstant.LOGIN_PATTERN.matcher(login).matches());
    }

    /**
     * Logs and builds the error of a broken limit.
     */
    private ValidationException invalid(String field, int limit) {
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                PullRequestShareExceptionLogConstant.PULL_REQUEST_SHARE_LIMIT_EXCEEDED, field, limit);
        return new ValidationException(
                ExceptionDomainKaizenError.VAL_PULL_REQUEST_SHARE_INVALID,
                Map.of(field, limit));
    }

    /**
     * Builds the error of a refused reviewer; the caller has logged the reason.
     */
    private static ValidationException reviewerRefused(UUID reviewerId) {
        return new ValidationException(
                ExceptionDomainKaizenError.VAL_PULL_REQUEST_SHARE_INVALID,
                Map.of(PullRequestShareConstant.FIELD_USER_ID, reviewerId));
    }
}
