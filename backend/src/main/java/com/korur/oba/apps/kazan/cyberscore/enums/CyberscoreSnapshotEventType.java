package com.korur.oba.apps.kazan.cyberscore.enums;

/**
 * Kind of check change that caused a cyberscore snapshot.
 */
public enum CyberscoreSnapshotEventType {

    // A check was created.
    CHECK_CREATED,

    // The weight or category of a check changed, with or without its outcome.
    CHECK_UPDATED,

    // Only the outcome of a check changed.
    CHECK_OUTCOME_CHANGED,

    // A check was deleted.
    CHECK_DELETED
}
