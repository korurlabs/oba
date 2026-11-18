package com.korur.oba.apps.kazan.pullrequest.constant.service;

/**
 * Log message constants for pull request share service operations.
 */
public final class PullRequestShareServiceLogConstant {

    private PullRequestShareServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String PULL_REQUEST_SHARE_CREATED = "Pull request share created: {}. Repository: {}, number: {}, reviewers: {}, relatedTickets: {}";
    public static final String PULL_REQUEST_SHARE_EDITED = "Pull request share edited: {}. Status: {}, reviewers: {}, relatedTickets: {}";
    public static final String PULL_REQUEST_SHARE_DELETED = "Pull request share deleted: {}";
    public static final String PULL_REQUEST_SHARE_APPROVAL_SET = "Pull request share {} approval by user {} set to {}";
}
