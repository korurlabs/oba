package com.korur.oba.connect.github.constant.client;

/**
 * Log messages of the GitHub REST client. No message carries a token or the private key.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class GithubClientLogConstant {

    private GithubClientLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String OPERATION_LIST_REPOSITORIES = "list repositories";
    public static final String OPERATION_LIST_PULL_REQUESTS = "list pull requests";
    public static final String OPERATION_SEARCH_PULL_REQUESTS = "search pull requests";
    public static final String OPERATION_GET_PULL_REQUEST = "get pull request";
    public static final String OPERATION_LIST_CONTRIBUTORS = "list contributors";

    public static final String CALL_REFUSED_NOT_CONFIGURED =
            "GitHub call refused: the integration is not configured. Operation: {}";
    public static final String REPOSITORIES_LOADED = "Loaded {} GitHub repositories of the installation";
    public static final String CONTRIBUTORS_LOADED = "Loaded {} GitHub contributors of {}";
    public static final String TOKEN_REJECTED_RETRYING =
            "GitHub rejected the installation token; fetching a new one and retrying once. Operation: {}";
    public static final String PULL_REQUEST_ABSENT = "GitHub has no pull request {} in {}";
    public static final String SEARCH_AUTHOR_UNKNOWN =
            "GitHub search refused an author filter; answering with an empty page. Repository: {}, Authors: {}";
    public static final String CALL_FAILED_STATUS = "GitHub call failed. Operation: {}, Status: {}";
    public static final String CALL_FAILED_IO = "GitHub call failed without an answer. Operation: {}";
}
