"use client";

import { useTranslations } from "next-intl";
import { AttachmentList } from "@oba/shared/features/attachments/components";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";

/**
 * The files attached to a page. Attachments of a page are kept as a record
 * (the server refuses their delete), so the list is read-only for everyone;
 * uploading belongs to the page's edit screen.
 */
export function PageAttachmentsCard({ pageId }: { pageId: string }) {
  const t = useTranslations("pageView.attachments");

  return (
    <Card role="region" aria-labelledby="page-attachments" className="gap-4">
      <CardHeader>
        <h2 id="page-attachments" className="text-lg leading-none">
          {t("title")}
        </h2>
      </CardHeader>
      <CardContent>
        <AttachmentList
          entityType={StorageEntityType.KALKAN_PAGE}
          entityId={pageId}
          readOnly
          emptyText={t("empty")}
        />
      </CardContent>
    </Card>
  );
}
