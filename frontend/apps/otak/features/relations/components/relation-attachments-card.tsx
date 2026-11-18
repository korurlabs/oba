"use client";

import { useTranslations } from "next-intl";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { EntityAttachments } from "@/features/attachments/components/entity-attachments";
import { useIsOrganizationMember } from "@/hooks/use-is-organization-admin";

/**
 * A relation's attachments, such as a signed NDA or the customer's RFP.
 * Every member lists, uploads, copies from a template and deletes them; a
 * guest only lists them, as the server refuses a guest's writes.
 */
export function RelationAttachmentsCard({ relationId }: { relationId: string }) {
  const t = useTranslations("relations.attachments");
  const isMember = useIsOrganizationMember();

  return (
    <Card role="region" aria-labelledby="attachments" className="gap-4">
      <CardHeader>
        <h2 id="attachments" className="text-lg leading-none">
          {t("title")}
        </h2>
      </CardHeader>
      <CardContent>
        <EntityAttachments
          entityType={StorageEntityType.RELATION}
          entityId={relationId}
          readOnly={!isMember}
        />
      </CardContent>
    </Card>
  );
}
