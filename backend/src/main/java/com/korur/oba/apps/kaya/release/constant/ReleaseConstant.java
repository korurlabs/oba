package com.korur.oba.apps.kaya.release.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Set;

// 4. Internal

/**
 * Field names, limits and violation messages of the pack order, the composition and releases.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ReleaseConstant {

    private ReleaseConstant() {
    }

    // --- Field names and error value keys ---
    public static final String FIELD_NUMBER = "number";
    public static final String FIELD_RELEASE = "release";
    public static final String FIELD_RELEASE_ID = "releaseId";
    public static final String FIELD_REPOSITORY_ID = "repositoryId";
    public static final String FIELD_PACK_ID = "packId";
    public static final String FIELD_VERSION_ID = "versionId";
    public static final String FIELD_ROOT_PATH = "rootPath";

    // --- Limits ---
    public static final int PACKS_MAX = 50;
    public static final String SHA256_ALGORITHM = "SHA-256";
    public static final int ROOT_PATH_MAX_LENGTH = 200;
    public static final String ROOT_PATH_PATTERN = "^[A-Za-z0-9_][A-Za-z0-9._-]*(/[A-Za-z0-9_][A-Za-z0-9._-]*){0,3}$";
    public static final String PATH_SEPARATOR = "/";
    // A mounted pack's AGENTS.md lies in its folder, so a folder below the delivered config
    // directories would put it among the files the packs at the root own.
    public static final Set<String> RESERVED_ROOTS = Set.of(".agents", ".claude", "docs");

    // --- Mount folder refusals ---
    public static final String ROOT_PATH_BLANK_MESSAGE = "Folder must be null for a pack at the root, or not blank";
    public static final String ROOT_PATH_INVALID_MESSAGE =
            "The folder must be 1 to 4 repository-relative segments of letters, digits, '.', '_' or '-', none starting with '.' or '-'";
    public static final String ROOT_PATH_RESERVED_MESSAGE = "The folder cannot start with %s, a folder of the delivered config";
    public static final String ROOT_PATH_TAKEN_MESSAGE = "The folder %s is given to two packs";
    public static final String ROOT_PATH_OVERLAP_MESSAGE = "The folder %s lies inside or around %s, the folder of another mounted pack";

    // --- Frontmatter keys ---
    public static final String FRONTMATTER_PATHS = "paths";
    public static final String FRONTMATTER_DENY = "deny";

    // --- Violation messages ---
    public static final String PATH_IN_TWO_OWNERS_MESSAGE = "Two composed packs that are not on one chain give this path";
    public static final String MOUNT_RULE_SCOPE_MESSAGE =
            "The rule %s of pack %s needs paths frontmatter whose every pattern lies under %s/";
    public static final String OVERRIDE_LOCKED_REQUIRED_MESSAGE =
            "The item %s is locked: pack %s is required, so no descendant can override it";
    public static final String OVERRIDE_LOCKED_GUARDRAIL_MESSAGE =
            "The item %s is locked: it is a guardrail of pack %s, so no descendant can override it";
    public static final String OVERRIDE_NOT_ALLOWED_MESSAGE =
            "Only a rule of a mounted pack can override a rule file of the packs at the root, and only its doc a convention doc";
    public static final String OVERRIDE_NOT_NARROWABLE_MESSAGE =
            "The paths frontmatter of the inherited rule %s cannot be rewritten for the mount folder";
    public static final String RULE_OVERRIDE_ALWAYS_ON_MESSAGE =
            "The inherited rule %s has no paths frontmatter; an always-on rule cannot be overridden";
    public static final String RULE_OVERRIDE_SPANS_MOUNT_MESSAGE =
            "The pattern %s of the inherited rule %s covers %s and other folders; narrow it in the pack that delivers it first";
    public static final String PACK_MOUNT_INHERITED_MESSAGE =
            "The pack is mounted in a folder and also composed at the root, itself or as an ancestor of a pack there";
    public static final String PACK_DUPLICATE_MESSAGE = "The pack is listed twice";
}
