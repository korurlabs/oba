"use client";

import { useTranslations } from "next-intl";
import type { StorageEntityType } from "../schemas";
import { AttachmentList } from "./attachment-list";
import { AttachmentUpload } from "./attachment-upload";

interface AttachmentSectionProps {
  entityType: StorageEntityType;
  /**
   * The owning entity. Renders nothing while undefined -- an upload
   * control cannot exist before the entity it uploads against, and the
   * backend requires a real UUID in the attachment metadata.
   */
  entityId: string | undefined;
  title?: string;
  /**
   * Attachment ids to hide from the list. See AttachmentList for why a
   * caller may already be rendering part of the collection elsewhere.
   */
  excludeIds?: readonly string[];
  /** Hides both the delete actions and the upload control. */
  readOnly?: boolean;
  /**
   * Enables DEFERRED mode when `entityId` is absent: the entity does not
   * exist yet (a ticket being created), so there is no list to show and
   * nothing to upload against. Files are staged and reported here; the
   * caller uploads them once the entity has been created.
   *
   * Without this, an absent `entityId` still renders nothing, so existing
   * callers are unaffected.
   */
  onStagedFilesChange?: (files: File[]) => void;
}

export function AttachmentSection({
  entityType,
  entityId,
  title,
  excludeIds,
  readOnly = false,
  onStagedFilesChange,
}: AttachmentSectionProps) {
  const t = useTranslations("attachments");

  // Deferred: no entity yet, but the caller wants to stage files for it.
  // There is no list to render -- nothing has been uploaded.
  if (!entityId) {
    if (!onStagedFilesChange || readOnly) return null;
    return (
      <div className="space-y-4">
        <h3 className="text-sm font-semibold">{title ?? t("section.title")}</h3>
        <AttachmentUpload
          entityType={entityType}
          onStagedFilesChange={onStagedFilesChange}
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold">
        {title ?? t("section.title")}
      </h3>
      <AttachmentList
        entityType={entityType}
        entityId={entityId}
        excludeIds={excludeIds}
        readOnly={readOnly}
      />
      {!readOnly && (
        <AttachmentUpload entityType={entityType} entityId={entityId} />
      )}
    </div>
  );
}
