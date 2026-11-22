import { isApiError } from "@oba/shared/api/axios";

/** The refusal of a restore whose parent page is still in the trash. */
const PARENT_DELETED = "KLK_STA_003";

/**
 * Whether a restore was refused because the page's parent is in the trash
 * too, so the parent has to be restored first.
 */
export function needsParentRestoredFirst(error: unknown): boolean {
  return isApiError(error, PARENT_DELETED);
}
