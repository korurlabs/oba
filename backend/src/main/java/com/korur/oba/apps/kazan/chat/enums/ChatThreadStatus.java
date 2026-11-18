package com.korur.oba.apps.kazan.chat.enums;

/**
 * Defines the state of a chat thread.
 *
 * The stored status is what the thread's creator or an organization owner or
 * admin set. Readers get the effective status, which also treats an {@link #ACTIVE}
 * thread as {@link #ARCHIVED} once it has been quiet for its auto-archive window.
 */
public enum ChatThreadStatus {

    // The thread accepts replies.
    ACTIVE,

    // The thread is read-only: no replies and no edits until it is reopened.
    CLOSED,

    // The thread is put away; a reply makes it ACTIVE again.
    ARCHIVED
}
