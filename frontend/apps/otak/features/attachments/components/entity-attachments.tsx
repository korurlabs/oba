"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { ExternalLink } from "lucide-react";
import { AttachmentList, AttachmentUpload } from "@oba/shared/features/attachments/components";
import {
  StorageEntityType,
  type AttachmentResponse,
} from "@oba/shared/features/attachments/schemas";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { useInvalidateMailAttachments } from "../hooks/use-document-snapshots";
import { isDocumentSnapshot } from "../lib/document-snapshots";
import { DocumentSnapshotPicker } from "./document-snapshot-picker";

export interface EntityAttachmentsProps {
  mailId: string;
  /** The mail's relation, whose documents the mail can carry. */
  relationId: string;
  /**
   * Hides every write action: the document select, the upload and the delete
   * buttons. Set when the server would refuse them, such as on a sent mail or
   * for a user below ADMIN.
   */
  readOnly: boolean;
}

/**
 * The attachments of a mail in two sections. "Documents" holds the copies of
 * the relation's documents, each with a link to its document, and adds one;
 * "Additional attachments" holds the files uploaded to the mail itself.
 */
export function EntityAttachments({ mailId, relationId, readOnly }: EntityAttachmentsProps) {
  const t = useTranslations("outreaches.mailAttachments");
  const documentsHeadingId = useId();
  const additionalHeadingId = useId();
  const invalidate = useInvalidateMailAttachments();

  const openDocument = (attachment: AttachmentResponse) =>
    attachment.documentId && (
      <Button asChild variant="ghost" size="sm">
        <Link href={`/documents/${attachment.documentId}`}>
          <ExternalLink aria-hidden="true" />
          {t("openDocument")}
        </Link>
      </Button>
    );

  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby={documentsHeadingId} className="flex flex-col gap-3">
        <div>
          <h3 id={documentsHeadingId} className="text-sm font-medium">
            {t("documentsTitle")}
          </h3>
          <p className="text-muted-foreground text-sm">{t("documentsHint")}</p>
        </div>
        {!readOnly && <DocumentSnapshotPicker mailId={mailId} relationId={relationId} />}
        <AttachmentList
          entityType={StorageEntityType.MAIL}
          entityId={mailId}
          readOnly={readOnly}
          include={isDocumentSnapshot}
          renderActions={openDocument}
          emptyText={t("documentsEmpty")}
          onDeleteSuccess={() => invalidate(mailId, { documents: true })}
        />
      </section>
      <section aria-labelledby={additionalHeadingId} className="flex flex-col gap-3">
        <h3 id={additionalHeadingId} className="text-sm font-medium">
          {t("additionalTitle")}
        </h3>
        <AttachmentList
          entityType={StorageEntityType.MAIL}
          entityId={mailId}
          readOnly={readOnly}
          include={(attachment) => !isDocumentSnapshot(attachment)}
          emptyText={t("additionalEmpty")}
          onDeleteSuccess={() => invalidate(mailId)}
        />
        {!readOnly && (
          <AttachmentUpload
            entityType={StorageEntityType.MAIL}
            entityId={mailId}
            onUploadSuccess={() => invalidate(mailId)}
          />
        )}
      </section>
    </div>
  );
}
