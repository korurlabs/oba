export * from "./components";
export * from "./hooks";
export * from "./schemas";
export { ATTACHMENT_ROUTES } from "./constants/api-routes";
export { checkAttachmentExists } from "./api";

// ── Library helpers (pure, no React) ─────────────────────────────────
export { parseAttachmentIds } from "./lib/parse-description-images";
export {
  collectOrphanAttachmentIds,
  deleteAttachments,
} from "./lib/orphan-gc";
export type { OrphanGCResult } from "./lib/orphan-gc";
export {
  commitPendingImages,
  reconcilePendingImages,
} from "./lib/commit-pending-images";
export type {
  CommitPendingImagesArgs,
  CommitPendingImagesResult,
} from "./lib/commit-pending-images";
