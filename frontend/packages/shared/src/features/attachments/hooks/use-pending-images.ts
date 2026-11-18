"use client";

import { useCallback, useState } from "react";

export interface PendingImage {
  /** Stable local identifier (crypto.randomUUID()) assigned at drop/paste time */
  localId: string;
  /** Compressed file ready for upload */
  file: File;
  /** Object URL for editor rendering; revoked after upload */
  blobUrl: string;
  /** Intrinsic width captured from canvas after compression */
  width: number;
  /** Intrinsic height captured from canvas after compression */
  height: number;
  /**
   * Set after a successful `uploadAttachments` call so retries skip already-
   * uploaded images. See spec section 7 #18 and section 8 "Network failure".
   */
  uploadedAttachmentId?: string;
  /**
   * Set alongside `uploadedAttachmentId` so a retry of `commitPendingImages`
   * can rewrite the description without re-uploading. Stored separately
   * from the id so the rewrite can reuse the previously returned Azure URL.
   */
  uploadedUrl?: string;
}

export interface UsePendingImagesReturn {
  pendingImages: Map<string, PendingImage>;
  /** Returns the generated `localId`. */
  addPendingImage: (image: Omit<PendingImage, "localId">) => string;
  removePendingImage: (localId: string) => void;
  /**
   * Looks up the pending image whose `blobUrl` matches `src` and removes
   * it (revoking the blob URL). No-op for non-matching srcs (e.g. Azure
   * URLs of already-saved images), so callers can pass any `<img src>`
   * without first checking whether it is pending.
   */
  removePendingImageBySrc: (src: string) => void;
  clearPendingImages: () => void;
  /**
   * Records that a pending image has been successfully uploaded. Goes
   * through `setPendingImages` so the next snapshot of the Map carries
   * `uploadedAttachmentId` / `uploadedUrl` -- replaces the previous
   * in-place mutation pattern that bypassed React state.
   */
  markUploaded: (localId: string, attachmentId: string, url: string) => void;
  hasPending: boolean;
}

/**
 * React hook that owns the in-memory map of images that have been dropped
 * or pasted into the editor but not yet uploaded. Owns blob URL lifetime:
 * `removePendingImage` and `clearPendingImages` revoke the matching object
 * URLs to prevent the underlying ArrayBuffers from leaking.
 */
export function usePendingImages(): UsePendingImagesReturn {
  const [pendingImages, setPendingImages] = useState<Map<string, PendingImage>>(
    () => new Map(),
  );

  const addPendingImage = useCallback(
    (image: Omit<PendingImage, "localId">): string => {
      const localId = crypto.randomUUID();
      setPendingImages((previous) => {
        const next = new Map(previous);
        next.set(localId, { ...image, localId });
        return next;
      });
      return localId;
    },
    [],
  );

  const removePendingImage = useCallback((localId: string): void => {
    setPendingImages((previous) => {
      const entry = previous.get(localId);
      if (!entry) {
        return previous;
      }
      URL.revokeObjectURL(entry.blobUrl);
      const next = new Map(previous);
      next.delete(localId);
      return next;
    });
  }, []);

  const removePendingImageBySrc = useCallback((src: string): void => {
    if (!src) return;
    setPendingImages((previous) => {
      let matchedId: string | null = null;
      for (const [id, entry] of previous) {
        if (entry.blobUrl === src) {
          matchedId = id;
          break;
        }
      }
      if (matchedId === null) {
        return previous;
      }
      const entry = previous.get(matchedId);
      if (entry) {
        URL.revokeObjectURL(entry.blobUrl);
      }
      const next = new Map(previous);
      next.delete(matchedId);
      return next;
    });
  }, []);

  const clearPendingImages = useCallback((): void => {
    setPendingImages((previous) => {
      if (previous.size === 0) {
        return previous;
      }
      previous.forEach((entry) => {
        URL.revokeObjectURL(entry.blobUrl);
      });
      return new Map();
    });
  }, []);

  const markUploaded = useCallback(
    (localId: string, attachmentId: string, url: string): void => {
      setPendingImages((previous) => {
        const entry = previous.get(localId);
        if (!entry) {
          return previous;
        }
        const next = new Map(previous);
        next.set(localId, {
          ...entry,
          uploadedAttachmentId: attachmentId,
          uploadedUrl: url,
        });
        return next;
      });
    },
    [],
  );

  return {
    pendingImages,
    addPendingImage,
    removePendingImage,
    removePendingImageBySrc,
    clearPendingImages,
    markUploaded,
    hasPending: pendingImages.size > 0,
  };
}
