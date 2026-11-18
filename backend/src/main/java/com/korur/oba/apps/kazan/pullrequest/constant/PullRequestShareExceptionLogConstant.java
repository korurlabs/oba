package com.korur.oba.apps.kazan.pullrequest.constant;

/**
 * Log message constants for pull request share domain exceptions.
 */
public final class PullRequestShareExceptionLogConstant {

    private PullRequestShareExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when a share is absent, deleted or of another organization.
     */
    public static final String PULL_REQUEST_SHARE_NOT_FOUND = "Pull request share not found: shareId={}, organizationId={}";

    /**
     * Log message for when a user edits or deletes another user's share.
     */
    public static final String PULL_REQUEST_SHARE_NOT_AUTHOR = "User is not the author of pull request share: shareId={}, userId={}";

    /**
     * Log message for when a user who is not a reviewer approves or withdraws.
     */
    public static final String PULL_REQUEST_SHARE_NOT_REVIEWER = "User is not a reviewer of pull request share: shareId={}, userId={}";

    /**
     * Log message for when a request breaks a size or range limit.
     */
    public static final String PULL_REQUEST_SHARE_LIMIT_EXCEEDED = "Pull request share exceeds a limit: field={}, limit={}";

    /**
     * Log message for when a request names a reviewer twice.
     */
    public static final String PULL_REQUEST_SHARE_DUPLICATE_REVIEWER = "Pull request share names a reviewer twice: userId={}";

    /**
     * Log message for when the author names themselves as a reviewer.
     */
    public static final String PULL_REQUEST_SHARE_AUTHOR_AS_REVIEWER = "Pull request share names its author as reviewer: userId={}";

    /**
     * Log message for when a reviewer is not an active member, admin or owner of the organization.
     */
    public static final String PULL_REQUEST_SHARE_REVIEWER_NOT_ELIGIBLE = "Pull request share reviewers not active members of the organization: userIds={}, organizationId={}";

    /**
     * Log message for when a request names a related ticket twice.
     */
    public static final String PULL_REQUEST_SHARE_DUPLICATE_RELATED_TICKET = "Pull request share names a related ticket twice: ticketId={}";

    /**
     * Log message for when an added related ticket is deleted or on a board the author is not a member of.
     */
    public static final String PULL_REQUEST_SHARE_RELATED_TICKETS_NOT_ACCESSIBLE = "Pull request share related tickets not accessible to the author: ticketIds={}";

    /**
     * Log message for when the organization has no pull requests channel.
     */
    public static final String PULL_REQUEST_CHANNEL_ABSENT = "Organization has no pull requests channel: organizationId={}";

    /**
     * Log message for when GitHub is not set up for the organization.
     */
    public static final String GITHUB_NOT_CONFIGURED = "GitHub is not configured for organization: organizationId={}";

    /**
     * Log message for when a repository is not one of the GitHub App installation's.
     */
    public static final String GITHUB_REPOSITORY_NOT_FOUND = "GitHub repository not in the installation: githubRepositoryId={}";

    /**
     * Log message for when GitHub has no such pull request.
     */
    public static final String GITHUB_PULL_REQUEST_NOT_FOUND = "GitHub pull request not found: repository={}, number={}";
}
