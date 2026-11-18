package com.korur.oba.apps.kazan.pullrequest.enums;

/**
 * Review state the author gives a shared pull request. The team sets it and
 * edits it later; it is never read back from GitHub, so a pull request that is
 * open on GitHub can be shared as in review or as merged for testing.
 */
public enum PullRequestShareStatus {

    // Not ready for review yet.
    DRAFT,

    // Ready, no reviewer has started.
    OPEN,

    // Reviewers are looking at it.
    IN_REVIEW,

    // Merged, for example for testing in an environment.
    MERGED,

    // Needs a conversation before it can go on.
    DISCUSS
}
