import type { RelationQueryRequest } from "../schemas";

/**
 * Query keys of the relations. Every key starts with the organization, so
 * the answers of one organization never show under another. Kept out of the
 * "use client" hook file so a server page can prefetch under the exact keys
 * the hooks read.
 */
export const RELATION_KEYS = {
  all: (organizationId: string) => ["relations", organizationId] as const,
  lists: (organizationId: string) => [...RELATION_KEYS.all(organizationId), "list"] as const,
  list: (organizationId: string, params: RelationQueryRequest) =>
    [...RELATION_KEYS.lists(organizationId), params] as const,
  detail: (organizationId: string, relationId: string) =>
    [...RELATION_KEYS.all(organizationId), "detail", relationId] as const,
  /** The battlefield: every lead and customer of the organization, every stage. */
  battlefield: (organizationId: string) =>
    [...RELATION_KEYS.all(organizationId), "battlefield"] as const,
  /** Every relation's attachment list, to refresh after a file of a mail changes. */
  attachmentLists: (organizationId: string) =>
    [...RELATION_KEYS.all(organizationId), "attachments"] as const,
  /** The files of one relation and of its mails, newest first. */
  attachments: (organizationId: string, relationId: string) =>
    [...RELATION_KEYS.attachmentLists(organizationId), relationId] as const,
  /** The members the owner select offers, with their Otak access. */
  ownerCandidates: (organizationId: string) =>
    [...RELATION_KEYS.all(organizationId), "ownerCandidates"] as const,
  /**
   * The mutation key of the battlefield's stage changes, so a settling move can
   * tell whether another one is still on its way.
   */
  stageChanges: (organizationId: string) =>
    [...RELATION_KEYS.all(organizationId), "stageChange"] as const,
};
