package com.korur.oba.apps.kazan.chat.enums;

/**
 * Defines what a chat channel is used for. Every purpose other than {@link #GENERAL}
 * names a fixed place of the organization, such as the target of automatic
 * posts, and is held by at most one live public channel of an organization,
 * so a post and a reader always know where it is.
 */
public enum ChatChannelPurpose {

    // A normal channel; an organization may have any number of them.
    GENERAL,

    // The channel that receives the daily update cards of the organization.
    DAILY_UPDATES,

    // The channel for the team's pull request talk.
    PULL_REQUESTS
}
