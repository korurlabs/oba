package com.korur.oba.apps.kaya.version.constant.service;

/**
 * Log messages of the version services, the not-found and refusal lines included.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class VersionServiceLogConstant {

    private VersionServiceLogConstant() {
    }

    // --- Lookup ---
    public static final String VERSION_NOT_FOUND = "Version {} not found in organization {}";
    public static final String DRAFT_NOT_OWN = "User {} is not the proposer {} of draft {}";
    public static final String DRAFT_NOT_DRAFT = "Version {} is {}, not a draft";
    public static final String DRAFT_FILE_NOT_FOUND = "Draft {} has no file {}";
    public static final String FILE_REFUSED = "Refused {} in draft {}: {} override marker violations";

    // --- Reads ---
    public static final String LISTING_VERSIONS = "Listing versions of organization {} with {}";
    public static final String READING_VERSION = "Reading version {} of organization {}";
    public static final String READING_FILE = "Reading file {} of version {} of organization {}";
    public static final String READING_FILE_HISTORY = "Reading the history of {} from version {} of organization {} for user {}";
    public static final String FILE_HISTORY_READ = "Found {} of {} versions changing {} on page {} of owner {}";
    public static final String DIFFING = "Diffing version {} against {}, path {}, in organization {}";

    // --- Drafts ---
    public static final String DRAFT_CREATED = "Created draft {} number {} of owner {} from base {} for user {}";
    public static final String FILE_SAVED = "Saved {} ({}) in draft {}";
    public static final String EXECUTABLE_NOT_ALLOWED = "Refused executable {} in draft {}: it is stored as a {}, not a hook or script";
    public static final String FILE_DELETED = "Deleted {} from draft {}";
    public static final String DRAFT_DELETED = "Deleted draft {} of owner {}";
    public static final String DRAFT_DELETE_REVIEWED = "Draft {} was reviewed (decided at {}); delete refused";
    public static final String REBASE_NOT_NEEDED = "Draft {} already rests on the current version {}";
    public static final String REBASE_CONFLICTS = "Rebase of draft {} on version {} has {} unresolved conflicts";
    public static final String REBASE_RESOLVED_TWICE = "Rebase of draft {} lists {} paths both to keep and to take";
    public static final String REBASED = "Rebased draft {} from base {} on version {}: {} paths taken from the current version";

    // --- Submit and decisions ---
    public static final String SUBMIT_REBASE_NEEDED = "Draft {} rests on {} but its owner is at version {}";
    public static final String SUBMIT_UNCHANGED = "Draft {} does not change its base {}";
    public static final String SUBMIT_IN_REVIEW = "Owner {} already has version {} in review; draft {} refused";
    public static final String SUBMITTED = "Submitted draft {} of owner {} for review, executable change {}";
    public static final String NOT_IN_REVIEW = "Version {} is {}, not in review; decision refused";
    public static final String APPROVE_BASE_NOT_CURRENT = "Version {} rests on {} but its owner is at version {}; approval refused";
    public static final String APPROVED = "Approved version {} of owner {}, superseding {}";
    public static final String REJECTED = "Rejected version {} of owner {}, back to its proposer {}";

    // --- Restore ---
    public static final String RESTORE_NOT_RELEASED = "Version {} is {} and was never released; restore refused";
    public static final String RESTORE_CURRENT = "Version {} is the current version of owner {}; nothing to restore";
    public static final String RESTORE_CREATED = "Created rollback draft {} number {} restoring version {} of owner {} for user {}";

    // --- CLI proposals ---
    public static final String PROPOSAL_IN_REVIEW = "Owner {} already has version {} in review; proposal of user {} refused";
    public static final String PROPOSAL_STALE = "Owner {} at version {} changed {} proposed paths since the release; "
            + "proposal of user {} refused";
    public static final String PROPOSAL_UNCHANGED = "Proposal for owner {} does not change version {}; user {}";
    public static final String PROPOSED = "Created and submitted CLI proposal {} number {} of owner {} on base {} with {} files, "
            + "executable change {}, for user {}";

    // --- Seeds ---
    public static final String SEED_UNCHANGED = "Seed of owner {} equals its current version {}; no version created";
    public static final String SEEDED = "Seeded version {} number {} of owner {} superseding {}: {} files, executable change {}, by {}";

    // --- Item overrides ---
    public static final String OVERRIDE_RECORDED = "Recorded override of {} in version {} on pack {} at version {}";
    public static final String OVERRIDE_REMOVED = "Removed override of {} from version {}";
    public static final String OVERRIDES_COPIED = "Copied {} overrides from version {} into version {}";
    public static final String OVERRIDE_BASE_SET = "Set the base of override {} in draft {} to pack {} version {}";
    public static final String OVERRIDE_BASE_STALE = "Override base {} of {} in draft {} is not the current version {} of the "
            + "nearest ancestor delivering it";
    public static final String OVERRIDE_ITEM_NOT_FOUND = "Draft {} has no item at {}";
    public static final String OVERRIDE_NOT_FOUND = "Draft {} records no override of {}";
    public static final String OVERRIDE_STILL_INHERITED = "Override of {} in draft {} not cleared: an ancestor still delivers it";
    public static final String OVERRIDE_CLEARED = "Cleared the override of {} in draft {}";
}
