package com.korur.oba.connect.push.enums;

/**
 * Why a user receives a push. The kind picks the preference switch that may silence it, the Android
 * channel, the copy and the iOS presentation; it travels to the app as the data field "type".
 *
 * Not persisted.
 */
public enum PushKind {

    // A direct mention of the user in a chat message.
    MENTION,

    // A reply to the user's message, or a message in a thread the user started.
    REPLY,

    // A chat message with @everyone in a channel the user can read.
    EVERYONE,

    // A chat message with @here in a channel the user can read, while the user was online.
    HERE,

    // A pull request share that asks the user for a review.
    REVIEW,

    // A reminder from a colleague to post today's daily update.
    NUDGE,

    // An urgent incident opened or raised on one of the user's boards.
    INCIDENT,

    // The user read a conversation on another device; a data-only push that lets each of the
    // user's devices withdraw that conversation's notifications.
    READ
}
