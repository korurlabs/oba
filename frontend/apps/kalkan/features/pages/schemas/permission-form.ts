import type { OrganizationMember } from "@oba/shared/features/organizations/schemas";
import {
  KalkanLevel,
  KalkanLevelSchema,
  type PagePermission,
  type PagePermissionRequest,
} from "./index";

// ── Draft list ──────────────────────────────────────────────────────

/**
 * One row of the access screen before it is saved. The screen edits the
 * whole list and saves it at once, because the server replaces every grant
 * of the root page with the list it is given.
 */
export type PermissionDraft = {
  /** The subject, as `user:<id>` or `group:<id>`; unique within the list. */
  key: string;
  userId: string | null;
  groupId: string | null;
  displayName: string;
  level: KalkanLevel;
};

function subjectKey(userId: string | null, groupId: string | null): string {
  return userId !== null ? `user:${userId}` : `group:${groupId ?? ""}`;
}

/** The draft list of the stored grants, sorted by name. */
export function draftsFromPermissions(permissions: readonly PagePermission[]): PermissionDraft[] {
  return permissions
    .map((permission) => ({
      key: subjectKey(permission.userId, permission.groupId),
      userId: permission.userId,
      groupId: permission.groupId,
      displayName: permission.displayName,
      level: permission.level,
    }))
    .sort((a, b) => a.displayName.localeCompare(b.displayName));
}

/** The member's display name: the full name, else the email. */
export function memberName(member: Pick<OrganizationMember, "firstName" | "lastName" | "email">) {
  const name = `${member.firstName} ${member.lastName}`.trim();
  return name === "" ? member.email : name;
}

/**
 * The levels a member may be given. The server refuses a guest anything
 * above VIEW (`KLK_VAL_003`), so the screen does not offer it.
 */
export function grantableLevels(role: OrganizationMember["role"] | undefined): KalkanLevel[] {
  return role === "GUEST" ? [KalkanLevel.VIEW] : [...KalkanLevelSchema.options];
}

/** The list with `member` added at `level`; unchanged when the member is already on it. */
export function addGrant(
  drafts: readonly PermissionDraft[],
  member: OrganizationMember,
  level: KalkanLevel
): PermissionDraft[] {
  const key = subjectKey(member.userId, null);
  if (drafts.some((draft) => draft.key === key)) {
    return [...drafts];
  }
  const allowed = grantableLevels(member.role);
  const added: PermissionDraft = {
    key,
    userId: member.userId,
    groupId: null,
    displayName: memberName(member),
    level: allowed.includes(level) ? level : KalkanLevel.VIEW,
  };
  return [...drafts, added].sort((a, b) => a.displayName.localeCompare(b.displayName));
}

/** The list with the level of the row `key` changed. */
export function changeGrantLevel(
  drafts: readonly PermissionDraft[],
  key: string,
  level: KalkanLevel
): PermissionDraft[] {
  return drafts.map((draft) => (draft.key === key ? { ...draft, level } : draft));
}

/** The list without the row `key`. */
export function removeGrant(drafts: readonly PermissionDraft[], key: string): PermissionDraft[] {
  return drafts.filter((draft) => draft.key !== key);
}

/** The PUT body: the full list, which replaces every live grant of the root page. */
export function toPermissionRequests(drafts: readonly PermissionDraft[]): PagePermissionRequest[] {
  return drafts.map((draft) => ({
    userId: draft.userId,
    groupId: draft.groupId,
    level: draft.level,
  }));
}

/** Whether the draft list differs from the stored grants, in subjects or levels. */
export function draftsChanged(
  drafts: readonly PermissionDraft[],
  stored: readonly PermissionDraft[]
): boolean {
  if (drafts.length !== stored.length) return true;
  const levels = new Map(stored.map((draft) => [draft.key, draft.level]));
  return drafts.some((draft) => levels.get(draft.key) !== draft.level);
}

/**
 * The members that can still be added: active ones not on the list, by name.
 * A disabled member is refused by the server (`KLK_VAL_002`).
 */
export function addableMembers(
  members: readonly OrganizationMember[],
  drafts: readonly PermissionDraft[]
): OrganizationMember[] {
  const listed = new Set(drafts.map((draft) => draft.key));
  return members
    .filter((member) => member.status === "ACTIVE" && !listed.has(subjectKey(member.userId, null)))
    .sort((a, b) => memberName(a).localeCompare(memberName(b)));
}
