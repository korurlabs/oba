import { deleteAttachment } from "../api";
import { parseAttachmentIds } from "./parse-description-images";

export interface OrphanGCResult {
  /** Ids that were sent to DELETE */
  attempted: string[];
  /** Ids whose DELETE call failed */
  failed: string[];
}

/**
 * Returns the set of attachment ids present in `prevDescription` but absent
 * from `nextDescription`. The diff is built from the unique ids found in
 * each HTML string -- duplicates within a single description are de-duped
 * via the Set semantics, which mirrors backend dedup (a single id may be
 * referenced by multiple `<img>` nodes pointing at the same blob).
 */
export function collectOrphanAttachmentIds(
  prevDescription: string,
  nextDescription: string,
): Set<string> {
  const prevIds = new Set(parseAttachmentIds(prevDescription));
  const nextIds = new Set(parseAttachmentIds(nextDescription));
  const orphans = new Set<string>();
  for (const id of prevIds) {
    if (!nextIds.has(id)) {
      orphans.add(id);
    }
  }
  return orphans;
}

/**
 * Calls `DELETE /storage/attachments/{id}` in parallel for each id via
 * `Promise.allSettled`, capturing failures rather than throwing. This is
 * a best-effort cleanup -- the caller (e.g. ticket save flow) must NOT
 * be blocked by an orphan DELETE failure.
 */
export async function deleteAttachments(
  ids: Iterable<string>,
): Promise<OrphanGCResult> {
  const attempted: string[] = Array.from(ids);
  if (attempted.length === 0) {
    return { attempted, failed: [] };
  }

  const results = await Promise.allSettled(
    attempted.map((id) => deleteAttachment(id)),
  );

  const failed: string[] = [];
  results.forEach((result, index) => {
    if (result.status === "rejected") {
      const id = attempted[index];
      if (id !== undefined) {
        failed.push(id);
      }
    }
  });

  return { attempted, failed };
}
