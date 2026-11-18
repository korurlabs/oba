import { uploadAttachments } from "../api";
import type { PendingImage } from "../hooks/use-pending-images";
import type { AttachmentResponse, StorageEntityType } from "../schemas";

export interface CommitPendingImagesArgs {
  entityType: StorageEntityType;
  /** UUID of the target entity (e.g. ticket id) */
  entityId: string;
  /**
   * HTML description string as it exists in the form at save time --
   * still contains `blob:` URLs for any image that has not yet been
   * committed to the backend.
   */
  description: string;
  /** Current pending images map keyed by `localId`. */
  pendingImages: Map<string, PendingImage>;
  /**
   * Optional callback invoked after each successful upload so the
   * caller's `pendingImages` map can be updated through React state
   * (instead of the previous in-place mutation, which bypassed
   * `setPendingImages` and produced stale snapshots on retry). When
   * provided, the caller's hook should mirror `attachmentId` / `url`
   * into the matching `PendingImage` entry. Optional so existing
   * non-React callers (and tests) can still use the helper.
   */
  markUploaded?: (
    localId: string,
    attachmentId: string,
    url: string,
  ) => void;
}

export interface CommitPendingImagesResult {
  /** Description with all `blob:` src values replaced by Azure URLs and a `data-attachment-id` attribute attached. */
  description: string;
  /** Attachment ids that were successfully uploaded in this commit. */
  uploadedAttachmentIds: string[];
}

/**
 * Escapes a string so it can be safely embedded in a regex literal.
 */
function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * Escapes a string so it can be safely embedded inside a double-quoted
 * HTML attribute value.
 */
function escapeAttr(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

/**
 * Rewrites a single `<img>` tag in the description that carries the given
 * blob `src`, swapping the src for the Azure URL and adding the
 * `data-attachment-id` attribute. Width and height are added only when
 * they are not already present on the matched tag.
 *
 * The matcher restricts itself to the `<img ...>` tag containing the
 * exact `src="<blobUrl>"` substring, so unrelated tags elsewhere in the
 * description are not touched.
 */
function rewriteImgTag(
  description: string,
  pendingImage: PendingImage,
  attachment: { id: string; url: string },
): string {
  const escapedBlob = escapeRegex(pendingImage.blobUrl);
  // Match the FULL <img ...> tag (non-greedy, no closing >) that contains
  // src="<blobUrl>". Capture the inner attribute string so we can rebuild
  // the tag with the additions while preserving any existing attributes.
  const tagRegex = new RegExp(
    `<img\\b([^>]*?\\ssrc="${escapedBlob}"[^>]*)>`,
    "i",
  );

  return description.replace(tagRegex, (_full, rawAttrs: string) => {
    let attrs = rawAttrs;

    // 1. Swap the blob src for the Azure URL.
    const srcRegex = new RegExp(`(\\ssrc=")${escapedBlob}(")`);
    attrs = attrs.replace(srcRegex, `$1${escapeAttr(attachment.url)}$2`);

    // 2. Add data-attachment-id (replace if it somehow already exists).
    if (/\sdata-attachment-id="[^"]*"/i.test(attrs)) {
      attrs = attrs.replace(
        /\sdata-attachment-id="[^"]*"/i,
        ` data-attachment-id="${escapeAttr(attachment.id)}"`,
      );
    } else {
      attrs += ` data-attachment-id="${escapeAttr(attachment.id)}"`;
    }

    // 3. Add width/height if not already on the tag.
    if (!/\swidth="[^"]*"/i.test(attrs)) {
      attrs += ` width="${pendingImage.width}"`;
    }
    if (!/\sheight="[^"]*"/i.test(attrs)) {
      attrs += ` height="${pendingImage.height}"`;
    }

    return `<img${attrs}>`;
  });
}

/**
 * Removes pending-images map entries whose `blobUrl` is no longer present
 * in `description`. This is the safety net for any deletion path the
 * NodeView trash button doesn't catch (keyboard backspace/delete,
 * select-all then type, undo, etc.) -- without it those removed-from-
 * editor images would still be uploaded on submit and orphan attachment
 * storage forever.
 *
 * Revokes the blob URL for entries that get filtered out.
 */
export function reconcilePendingImages(
  description: string,
  pendingImages: Map<string, PendingImage>,
): Map<string, PendingImage> {
  const present = new Map<string, PendingImage>();
  for (const [localId, image] of pendingImages) {
    if (description.includes(`src="${image.blobUrl}"`)) {
      present.set(localId, image);
    } else {
      URL.revokeObjectURL(image.blobUrl);
    }
  }
  return present;
}

/**
 * Uploads all pending images sequentially, rewrites the `blob:` `src`
 * values in the description string to the returned Azure URLs, and
 * returns the resolved description.
 *
 * Sequential (NOT `Promise.all`) so the first failure short-circuits;
 * the caller is responsible for not patching the entity in that case.
 *
 * Retry-resilience: each `PendingImage` carries an optional
 * `uploadedAttachmentId`. If it is already set (because a previous run
 * partially succeeded) the upload is skipped and the previously returned
 * id is reused for the rewrite.
 */
export async function commitPendingImages(
  args: CommitPendingImagesArgs,
): Promise<CommitPendingImagesResult> {
  let description = args.description;
  const uploadedAttachmentIds: string[] = [];

  for (const pendingImage of args.pendingImages.values()) {
    let attachmentId: string;
    let attachmentUrl: string;

    if (pendingImage.uploadedAttachmentId && pendingImage.uploadedUrl) {
      // Already uploaded on a previous attempt -- the description still
      // contains the blob: src (because the PATCH did not run), so the
      // rewrite below still needs to happen, but the network upload can
      // be skipped entirely. See spec section 7 #18.
      attachmentId = pendingImage.uploadedAttachmentId;
      attachmentUrl = pendingImage.uploadedUrl;
    } else {
      // The shared Axios client throws an Error(message) on failure, so
      // any rejection here propagates straight to the caller and the
      // remaining iterations are skipped.
      const uploadResponse = await uploadAttachments(
        args.entityType,
        args.entityId,
        [pendingImage.file],
      );
      // We send a single file at a time, so the response array has one entry.
      const uploaded: AttachmentResponse | undefined = uploadResponse.data?.[0];
      if (!uploaded) {
        throw new Error("UPLOAD_RETURNED_NO_ATTACHMENT");
      }
      attachmentId = uploaded.id;
      attachmentUrl = uploaded.url;
      // Notify the caller through React state so the next snapshot of
      // the pending-images Map carries the upload metadata. Falls back
      // to a no-op for callers that don't supply the callback (tests,
      // migration tooling, etc.) -- the in-flight retry path still
      // works because the same in-memory entry sees the assignment via
      // the markUploaded callback when wired by the kazan ticket sheet.
      args.markUploaded?.(pendingImage.localId, attachmentId, attachmentUrl);
    }

    description = rewriteImgTag(description, pendingImage, {
      id: attachmentId,
      url: attachmentUrl,
    });
    uploadedAttachmentIds.push(attachmentId);
  }

  return { description, uploadedAttachmentIds };
}
