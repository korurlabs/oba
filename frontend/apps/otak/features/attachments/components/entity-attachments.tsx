"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { FilePlus } from "lucide-react";
import { AttachmentList, AttachmentUpload } from "@oba/shared/features/attachments/components";
import { Button } from "@oba/ui/sky/button";
import { AttachmentFromTemplateDialog } from "@/features/document-templates/components/attachment-from-template-dialog";
import type { DocumentTemplateAttachmentEntityType } from "@/features/document-templates/schemas";

export interface EntityAttachmentsProps {
  entityType: DocumentTemplateAttachmentEntityType;
  entityId: string;
  /**
   * Hides every write action: the delete buttons, the upload and the
   * template copy. Set when the server would refuse them, such as on a sent
   * mail or for a user below the role the parent needs.
   */
  readOnly: boolean;
}

/**
 * The attachments of a mail or a relation: the list, an upload, and "Add
 * from template", which stores a copy of a document template's PDF.
 */
export function EntityAttachments({ entityType, entityId, readOnly }: EntityAttachmentsProps) {
  const t = useTranslations("documentTemplates.attach");
  const [fromTemplateOpen, setFromTemplateOpen] = useState(false);

  return (
    <div className="flex flex-col gap-4">
      {!readOnly && (
        <div className="flex justify-end">
          <Button variant="outline" size="sm" onClick={() => setFromTemplateOpen(true)}>
            <FilePlus aria-hidden="true" />
            {t("button")}
          </Button>
        </div>
      )}
      <AttachmentList entityType={entityType} entityId={entityId} readOnly={readOnly} />
      {!readOnly && <AttachmentUpload entityType={entityType} entityId={entityId} />}
      {!readOnly && (
        <AttachmentFromTemplateDialog
          entityType={entityType}
          entityId={entityId}
          open={fromTemplateOpen}
          onOpenChange={setFromTemplateOpen}
        />
      )}
    </div>
  );
}
