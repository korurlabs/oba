package com.korur.oba.apps.kaya.core.exception.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes of Kaya (KAY), shared by every Kaya domain.
 *
 * The middle part of a code (SEC, VAL, NF, CFG, PLN, PIP) groups the codes for clients; the CFG
 * codes keep their numbers across the domains that version and deliver agent files, and their
 * constant names carry the domain that raises them. The
 * category decides the HTTP status and the exception that may carry the code, so a
 * KAY_VAL code that answers 409 has the STA category and is thrown as a ConflictException.
 * A code that answers two statuses has one constant per status. Users, organizations and
 * memberships belong to the platform, so their refusals carry platform codes, not KAY codes.
 */
public enum ExceptionDomainKayaError implements ExceptionDomainError {

    // ── Security (KAY_SEC) ───────────────────────────────────────────────────
    SEC_MEMBERSHIP_DISABLED("KAY_SEC_002", "The membership of this user or its organization is disabled.", ExceptionCategory.SEC),
    SEC_MACHINE_TOKEN_INVALID("KAY_SEC_003", "The machine token is missing, unknown, revoked or expired, or its machine is retired.", ExceptionCategory.AUT),
    SEC_RATE_LIMITED("KAY_SEC_004", "Too many requests for this machine token.", ExceptionCategory.API),
    SEC_GIT_IDENTITY_UNKNOWN("KAY_SEC_007", "This git email is not registered on any user of the organization.", ExceptionCategory.SEC),

    // ── Validation (KAY_VAL) ─────────────────────────────────────────────────
    VAL_SESSION_OWNED_BY_OTHER_USER("KAY_VAL_001", "This session belongs to another user.", ExceptionCategory.STA),
    VAL_REPOSITORY_ARCHIVED("KAY_VAL_002", "The repository is archived.", ExceptionCategory.VAL),
    // KAY_VAL_005 is retired: it refused a batch whose machine name was not the token's machine; the token alone identifies the machine.
    VAL_MACHINE_NAME_DUPLICATE("KAY_VAL_006", "The user already has a machine with this machine name.", ExceptionCategory.STA),
    // KAY_VAL_007 is retired: it refused merging two people, which no longer exists.
    VAL_USAGE_OUT_OF_RANGE("KAY_VAL_009", "The usage values are out of range.", ExceptionCategory.API),
    // KAY_VAL_010 is retired: it refused changes to a merged person, which no longer exists.
    VAL_MACHINE_RETIRED("KAY_VAL_011", "The machine is retired; register the machine again to get a new token.", ExceptionCategory.STA),
    VAL_BODY_TOO_LARGE("KAY_VAL_012", "The request body exceeds the size limit.", ExceptionCategory.API),
    VAL_EVENT_INVALID("KAY_VAL_013", "An event of the batch is invalid.", ExceptionCategory.API),
    VAL_BATCH_EMPTY("KAY_VAL_014", "A batch needs events or usage.", ExceptionCategory.API),
    VAL_LINK_REFERENCE_INVALID("KAY_VAL_015", "A Kazan ticket must be a key such as SEN-42, a pull request a https://github.com/ URL.", ExceptionCategory.VAL),
    VAL_ACCOUNT_LEARNED_CONCURRENTLY("KAY_VAL_016", "The account was recorded by a concurrent batch; send the batch again.", ExceptionCategory.STA),

    // ── Not Found (KAY_NF) ───────────────────────────────────────────────────
    NOT_RESOURCE_FOUND("KAY_NF_001", "The session, machine, user or repository was not found.", ExceptionCategory.NOT),
    NOT_VERSIONED_RESOURCE_FOUND("KAY_NF_001", "The pack, version, file or blob was not found.", ExceptionCategory.NOT),
    NOT_PLAN_FOUND("KAY_NF_001", "The plan, plan version or feedback was not found.", ExceptionCategory.NOT),
    NOT_LESSON_FOUND("KAY_NF_001", "The lesson or its repository was not found.", ExceptionCategory.NOT),

    // ── Versioned agent files (KAY_CFG): packs, versions, releases and their delivery ──
    // validation
    VALIDATION_RULES_BROKEN("KAY_CFG_001", "The files or the repository composition break one or more validation rules.", ExceptionCategory.VAL),
    // version
    VERSION_REBASE_REQUIRED("KAY_CFG_002", "The draft must be rebased on the current version, or the rebase has conflicts.", ExceptionCategory.STA),
    VERSION_NOT_A_DRAFT("KAY_CFG_014", "The version is not a draft; only a draft is edited, rebased, submitted or deleted.", ExceptionCategory.STA),
    VERSION_IN_REVIEW("KAY_CFG_015", "Another version of this pack is already in review.", ExceptionCategory.STA),
    VERSION_DRAFT_UNCHANGED("KAY_CFG_016", "The draft does not change its base version.", ExceptionCategory.STA),
    VERSION_NOT_RELEASED("KAY_CFG_018", "Only an approved or superseded version can be restored.", ExceptionCategory.STA),
    VERSION_DRAFT_REVIEWED("KAY_CFG_020", "The draft has been reviewed; it is kept with its reviews and cannot be deleted.", ExceptionCategory.STA),
    // release
    RELEASE_PATH_IN_TWO_OWNERS("KAY_CFG_003", "A path of the repository is given by two packs.", ExceptionCategory.STA),
    RELEASE_REMOTE_CHANGED("KAY_CFG_012", "The release was signed for an earlier remote URL of the repository; a new release is needed.", ExceptionCategory.STA),
    // review
    REVIEW_NOT_ALLOWED("KAY_CFG_004", "This user may not review this version.", ExceptionCategory.SEC),
    REVIEW_VERSION_NOT_IN_REVIEW("KAY_CFG_017", "The version is not in review; only a version in review is reviewed.", ExceptionCategory.STA),
    // blob
    BLOB_HASH_MISMATCH("KAY_CFG_005", "The content does not match its SHA-256 hash.", ExceptionCategory.API),
    // KAY_CFG_007 and KAY_CFG_013 are retired: they refused revocation rules and lifts, which no longer exist.
    // pack tree and mounts
    PACK_NAME_TAKEN("KAY_CFG_008", "A pack with this name already exists in the organization.", ExceptionCategory.STA),
    PACK_IN_USE("KAY_CFG_035", "This pack is already used, so its name can no longer change.", ExceptionCategory.STA),
    PACK_MOUNT_INVALID("KAY_CFG_026", "The mount folder is not a valid repository path, or it lies inside or around another mount's folder.", ExceptionCategory.VAL),
    ITEM_LOCKED("KAY_CFG_031", "The item is locked by a required pack or is a guardrail; a child pack cannot override it.", ExceptionCategory.VAL),
    OVERRIDE_BASE_STALE("KAY_CFG_032", "The parent changed again; review its current version first.", ExceptionCategory.STA),
    MOUNT_RULE_UNSCOPED("KAY_CFG_034", "A rule of a pack mounted in a folder needs paths frontmatter whose every pattern lies under that folder.", ExceptionCategory.VAL),
    // seed
    SEED_BLOBS_MISSING("KAY_CFG_009", "Content the seed names has not been uploaded.", ExceptionCategory.VAL),
    SEED_NAME_TAKEN("KAY_CFG_010", "A pack with this name already exists in the organization.", ExceptionCategory.STA),
    SEED_TOO_LARGE("KAY_CFG_011", "The seed names more files or more content than one run takes.", ExceptionCategory.API),
    SEED_PARENT_FIXED("KAY_CFG_033", "The pack exists with another parent.", ExceptionCategory.STA),
    // delivery
    DELIVERY_PROPOSAL_SPANS_OWNERS("KAY_CFG_019", "A proposal changes the paths of one pack only; these paths come from several.", ExceptionCategory.VAL),
    // render
    RENDER_INVALID_FILE("KAY_CFG_027", "The file cannot be read: its frontmatter, JSON or YAML is invalid, or a typed field is missing or of the wrong type.", ExceptionCategory.VAL),
    RENDER_NOT_DELIVERABLE("KAY_CFG_028", "No file kind accepts this path, so Kaya does not deliver it.", ExceptionCategory.VAL),

    // ── Plans (KAY_PLN) ──────────────────────────────────────────────────────
    PLN_VERSION_NOT_LATEST("KAY_PLN_001", "Only the latest version of a plan is approved or sent back for changes.", ExceptionCategory.STA),
    PLN_FEEDBACK_INVALID("KAY_PLN_002", "The feedback needs a comment unless it approves, and both line bounds within the version or none.", ExceptionCategory.VAL),
    PLN_FEEDBACK_DELIVERED("KAY_PLN_003", "The feedback has been delivered to a session and can no longer be withdrawn.", ExceptionCategory.STA),
    PLN_DELIVERY_QUERY_INVALID("KAY_PLN_004", "Give either session ids, or a remote URL and a branch.", ExceptionCategory.API);

    // KAY_BRN_001 to KAY_BRN_003 are retired: they refused approvals and cancellations of branch runs, which no longer exist.

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainKayaError(String code, String message, ExceptionCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ExceptionCategory getCategory() {
        return category;
    }
}
