import { KayaApiError } from "./errors";

/**
 * Plain words for the refusals a person can cause from a form or a button,
 * keyed by error code. Codes not listed fall back to the server's message.
 */
const REFUSALS: Readonly<Record<string, string>> = {
  GEN_SEC_003: "Your role does not allow this.",
  GEN_SEC_002: "You no longer have access to this organization.",
  ORG_SEC_002: "You no longer have access to this organization.",
  ORG_SEC_003: "Your role does not allow this.",
  ORG_SEC_005: "This organization is disabled.",
  GEN_VAL_001: "Kaya refused the values entered.",
  KAY_NF_001: "It no longer exists in this organization.",
  KAY_SEC_007:
    "This git email belongs to no member of the organization. Commit with the email you sign in with, or ask an admin.",
  KAY_VAL_006: "This user already has an active machine with that name.",
  KAY_VAL_011: "The machine is retired. Register it again to get a new token.",
  KAY_CFG_001: "Kaya refused the configuration.",
  KAY_CFG_003: "Two packs give the same path.",
  KAY_CFG_009: "Some content the seed names was not uploaded. Run the seed again.",
  KAY_CFG_008: "A pack with this name already exists in the organization.",
  KAY_CFG_010: "A pack with this name already exists in the organization.",
  KAY_CFG_026:
    "The mount folder is not a valid repository folder, or it lies inside or around another mount's folder.",
  KAY_CFG_031:
    "The item is locked by a required pack or is a guardrail, so a child pack cannot override it.",
  KAY_CFG_032: "The parent changed again. Review its current version first.",
  KAY_CFG_033: "The pack already exists with another parent.",
  KAY_CFG_034:
    "A rule of a pack mounted in a folder needs paths frontmatter whose every pattern lies under that folder.",
  KAY_CFG_035: "This pack is already used, so its name can no longer change.",
  KAY_CFG_011: "The seed names more files or more content than one run takes.",
  KAY_CFG_002:
    "The pack has a newer version than this change is based on, so the change must be rebased first.",
  KAY_CFG_004: "You may not review this change.",
  KAY_CFG_014:
    "This change is no longer open; only an open change is edited, rebased, submitted or deleted.",
  KAY_CFG_015:
    "Another change of this pack is already in review; it must be approved or rejected first.",
  KAY_CFG_016: "Nothing would change: the files are the same as the version this is based on.",
  KAY_CFG_017: "This change is no longer in review; it was approved or sent back to its proposer.",
  KAY_CFG_018: "Only a released version, approved or superseded, can be restored.",
  KAY_CFG_019: "A proposal changes the files of one pack only; these files come from several.",
  KAY_CFG_027:
    "Kaya cannot read this file: its frontmatter, JSON or YAML is invalid, or a field is missing or of the wrong type.",
  KAY_CFG_028: "No file kind is delivered at this path, so Kaya does not take it.",
  KAY_CFG_020: "This change was reviewed, so it is kept with its reviews and cannot be deleted.",
  KAY_VAL_002: "The repository is archived and takes no changes.",
};

/** A GEN_SEC_002 naming a proposer refuses a write to another member's change. */
const DRAFT_OF_ANOTHER = "Only the member who started this change can edit it.";

/** A GEN_SEC_003 without a required role refuses an action only the user themselves may run. */
const SELF_ONLY = "Only the user themselves can do this.";

/**
 * Texts for the config rules whose server message names a path or target
 * rather than the reason (`errorValues.violations[].rule` of KAY_CFG_001,
 * KAY_CFG_003, KAY_CFG_027, KAY_CFG_031 and KAY_CFG_034). Other rules keep
 * the server's message.
 */
const VIOLATION_RULES: Readonly<Record<string, string>> = {
  SYMLINK_CONTENT_REQUIRED:
    "This path must be a regular file, since Claude Code loads it at start or Kaya checks the settings rules in it.",
  SYMLINK_TARGET_NOT_ALLOWED: "The symlink points outside the paths Kaya delivers.",
  PATH_THROUGH_SYMLINK:
    "The path lies below a delivered symlink, so it would be written wherever that link points.",
  HOOK_SYMLINK:
    "A hook must be a regular file, so that every change to the code it runs needs an owner's approval.",
  PATH_CASE_CONFLICT:
    "The path differs from another one only in letter case; a case-insensitive file system writes both to one place.",
};

function asSentence(text: string): string {
  return /[.!?]$/.test(text) ? text : `${text}.`;
}

/** One violation of a refused configuration as "path: reason". */
function violationText(value: unknown): string | null {
  if (typeof value !== "object" || value === null) return null;
  const { path, rule, message } = value as Record<string, unknown>;
  const reason =
    (typeof rule === "string" && VIOLATION_RULES[rule]) ||
    (typeof message === "string" && message !== "" ? message : null) ||
    (typeof rule === "string" && rule !== "" ? rule : null);
  if (reason === null) return null;
  const sentence = asSentence(reason);
  return typeof path === "string" && path !== "" ? `${path}: ${sentence}` : sentence;
}

/** What a too-large seed exceeded, from the limit and count the server sent. */
function seedLimitText(values: Readonly<Record<string, unknown>>): string | null {
  const { limitFiles, fileCount, limitBytes, contentBytes } = values;
  if (typeof limitFiles === "number" && typeof fileCount === "number") {
    return `It names ${fileCount} files; the limit is ${limitFiles}.`;
  }
  if (typeof limitBytes === "number" && typeof contentBytes === "number") {
    return `It holds ${contentBytes} bytes of content; the limit is ${limitBytes}.`;
  }
  return null;
}

/** How a path changed on one side of a rebase (`FileChange`). */
const CHANGE_VERBS: Readonly<Record<string, string>> = {
  ADDED: "added",
  MODIFIED: "changed",
  DELETED: "deleted",
};

/**
 * One rebase conflict as "path: your edits changed it, the current version
 * deleted it." A CLI proposal reports a null side of the change for a path it left
 * as the release delivered it.
 */
function conflictText(value: unknown): string | null {
  if (typeof value !== "object" || value === null) return null;
  const { path, draftChange, currentChange } = value as Record<string, unknown>;
  if (typeof path !== "string" || path === "") return null;
  const draft =
    typeof draftChange === "string" && CHANGE_VERBS[draftChange]
      ? `your edits ${CHANGE_VERBS[draftChange]} it`
      : "your edits leave it as delivered";
  const current =
    typeof currentChange === "string" && CHANGE_VERBS[currentChange]
      ? `the current version ${CHANGE_VERBS[currentChange]} it`
      : "the current version changed it";
  return `${path}: ${draft}, ${current}.`;
}

/** Why a reviewer was refused (403 KAY_CFG_004, `errorValues.reason` and `requiredRole`). */
function reviewRefusalText(values: Readonly<Record<string, unknown>>): string {
  if (values.reason === "PROPOSER") {
    return "You proposed this change, so another admin or owner must review it.";
  }
  if (values.reason === "ROLE") {
    return values.requiredRole === "OWNER"
      ? "This change touches executable code, hooks or permissions; only an owner approves it."
      : "Only an admin or owner reviews changes.";
  }
  return REFUSALS.KAY_CFG_004 as string;
}

/** The paths of each owner a too-wide proposal named (KAY_CFG_019, `errorValues.owners`). */
function proposalOwnersText(owners: unknown): string | null {
  if (typeof owners !== "object" || owners === null) return null;
  const groups = Object.values(owners as Record<string, unknown>)
    .filter((paths): paths is unknown[] => Array.isArray(paths))
    .map((paths) => paths.filter((entry): entry is string => typeof entry === "string").join(", "))
    .filter((group) => group !== "");
  return groups.length > 1 ? `Propose each group on its own: ${groups.join("; ")}.` : null;
}

/** The sentence to show a person for a failed call. */
export function describeError(error: unknown): string {
  if (!(error instanceof KayaApiError)) {
    return error instanceof Error ? error.message : String(error);
  }
  if (error.code === "GEN_VAL_001") {
    const details = Object.values(error.errorValues).filter(
      (value): value is string => typeof value === "string"
    );
    return details.length > 0
      ? `${REFUSALS.GEN_VAL_001} ${details.join(" ")}`
      : (REFUSALS.GEN_VAL_001 as string);
  }
  if (error.code === "GEN_SEC_002" && error.errorValues.proposedBy !== undefined) {
    return DRAFT_OF_ANOTHER;
  }
  if (error.code === "GEN_SEC_003" && error.errorValues.requiredRole === undefined) {
    return error.errorValues.userId !== undefined ? SELF_ONLY : (REFUSALS.GEN_SEC_003 as string);
  }
  if (error.code === "KAY_CFG_026") {
    const reason = error.errorValues.rootPath;
    return typeof reason === "string" && reason !== ""
      ? `${REFUSALS.KAY_CFG_026 as string} ${asSentence(reason)}`
      : (REFUSALS.KAY_CFG_026 as string);
  }
  if (
    error.code === "KAY_CFG_001" ||
    error.code === "KAY_CFG_003" ||
    error.code === "KAY_CFG_031" ||
    error.code === "KAY_CFG_034"
  ) {
    const violations = error.errorValues.violations;
    const details = (Array.isArray(violations) ? violations : [])
      .map(violationText)
      .filter((text): text is string => text !== null);
    return [REFUSALS[error.code] as string, ...details].join(" ");
  }
  if (error.code === "KAY_CFG_002") {
    const conflicts = error.errorValues.conflicts;
    const details = (Array.isArray(conflicts) ? conflicts : [])
      .map(conflictText)
      .filter((text): text is string => text !== null);
    return details.length > 0
      ? [
          REFUSALS.KAY_CFG_002 as string,
          "These paths changed on both sides; keep your edits or the current version's content for each:",
          ...details,
        ].join(" ")
      : (REFUSALS.KAY_CFG_002 as string);
  }
  if (error.code === "KAY_CFG_004") {
    return reviewRefusalText(error.errorValues);
  }
  if (error.code === "KAY_CFG_019") {
    const groups = proposalOwnersText(error.errorValues.owners);
    return groups === null ? (REFUSALS.KAY_CFG_019 as string) : `${REFUSALS.KAY_CFG_019} ${groups}`;
  }
  if (error.code === "KAY_CFG_027" || error.code === "KAY_CFG_028") {
    // A file that parsed but breaks a delivery rule comes back with the
    // broken rules; naming them tells the person more than the parse headline.
    const violations = error.errorValues.violations;
    const details = (Array.isArray(violations) ? violations : [])
      .map(violationText)
      .filter((text): text is string => text !== null);
    if (details.length > 0) {
      return [REFUSALS.KAY_CFG_001 as string, ...details].join(" ");
    }
    const path = error.errorValues.path;
    const reason = error.errorValues.reason;
    const where = typeof path === "string" && path !== "" ? `${path}: ` : "";
    return typeof reason === "string" && reason !== ""
      ? `${REFUSALS[error.code] as string} ${where}${reason}.`
      : (REFUSALS[error.code] as string);
  }
  if (error.code === "KAY_CFG_009") {
    const missing = error.errorValues.missing;
    return Array.isArray(missing) && missing.length > 0
      ? `${REFUSALS.KAY_CFG_009} ${missing.length} ${missing.length === 1 ? "file is" : "files are"} missing.`
      : (REFUSALS.KAY_CFG_009 as string);
  }
  if (error.code === "KAY_CFG_011") {
    const limit = seedLimitText(error.errorValues);
    return limit === null ? (REFUSALS.KAY_CFG_011 as string) : `${REFUSALS.KAY_CFG_011} ${limit}`;
  }
  return (error.code !== null && REFUSALS[error.code]) || error.message;
}
