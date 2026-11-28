"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { ArrowLeft } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ActionEmptyState } from "@/components/page-states";
import { ContactsCard } from "@/features/contacts/components/contacts-card";
import { useEvent } from "@/features/events/hooks/use-events";
import { OutreachesCard } from "@/features/outreaches/components/outreaches-card";
import { ReachOutDialog } from "@/features/outreaches/components/reach-out-dialog";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useDeleteRelation, useRelation, useUpdateRelation } from "../hooks/use-relations";
import { relationErrorText } from "../lib/relation-error";
import {
  RelationType,
  relationToFormValues,
  toRelationUpdateRequest,
  type RelationDetail,
} from "../schemas";
import { DeleteRelationDialog } from "./delete-relation-dialog";
import { RelationAttachmentsCard } from "./relation-attachments-card";
import { applyRelationFieldErrors, RelationForm } from "./relation-form";
import { RelationNotesCard } from "./relation-notes-card";
import { RelationStageBadge } from "./relation-stage-badge";
import { RelationTypeActions } from "./relation-type-actions";

/**
 * One relation: a heading with its name, type, stage, event and the type
 * change, the edit form in its Company, Address and Sales panels, its
 * contacts, its attachments, its outreaches and its notes. An OWNER or ADMIN
 * also gets the delete action, which the server refuses to anyone else, and on
 * a lead the Reach out dialog.
 */
export function RelationDetailView({ relationId }: { relationId: string }) {
  const t = useTranslations("relations");
  const tErrors = useTranslations("errors");
  const relation = useRelation(relationId);
  const data = relation.data?.data;

  if (data) {
    return <RelationDetailContent relation={data} />;
  }

  if (relation.isError) {
    if (isApiError(relation.error, "OTK_NOT_001")) {
      return (
        <ActionEmptyState
          title={t("detail.notFoundTitle")}
          description={t("detail.notFoundDescription")}
          action={
            <Button asChild variant="outline">
              <Link href="/relations">
                <ArrowLeft aria-hidden="true" />
                {t("detail.back")}
              </Link>
            </Button>
          }
        />
      );
    }
    return (
      <LoadError
        title={t("detail.loadError")}
        message={relationErrorText(relation.error, t, "errors.loadHint")}
        retryLabel={tErrors("tryAgain")}
        onRetry={() => void relation.refetch()}
      />
    );
  }

  return <TableLoading label={t("detail.loading")} />;
}

function RelationDetailContent({ relation }: { relation: RelationDetail }) {
  const t = useTranslations("relations");
  const router = useRouter();
  const { formatDate } = useDateFormat();
  const isAdmin = useIsOrganizationAdmin();
  const updateRelation = useUpdateRelation(relation.id);
  const deleteRelation = useDeleteRelation();

  const initialValues = useMemo(() => relationToFormValues(relation), [relation]);

  const handleDelete = () => {
    deleteRelation.mutate(relation.id, {
      onSuccess: () => router.replace("/relations"),
    });
  };

  return (
    <div className="flex flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="w-fit">
        <Link href="/relations">
          <ArrowLeft aria-hidden="true" />
          {t("detail.back")}
        </Link>
      </Button>

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-2">
          <h1 className="break-words">{relation.name}</h1>
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{t(`types.${relation.type}`)}</Badge>
            {relation.stage && <RelationStageBadge stage={relation.stage} />}
            {relation.eventId && <RelationEventBadge eventId={relation.eventId} />}
            <span className="text-muted-foreground text-sm tabular-nums">
              {t("detail.timestamps", {
                created: formatDate(relation.createdAt),
                updated: formatDate(relation.updatedAt),
              })}
            </span>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {isAdmin && relation.type === RelationType.LEAD && (
            <ReachOutDialog relationId={relation.id} relationName={relation.name} />
          )}
          <RelationTypeActions relation={relation} />
          {isAdmin && (
            <DeleteRelationDialog
              relationName={relation.name}
              isDeleting={deleteRelation.isPending}
              onConfirm={handleDelete}
            />
          )}
        </div>
      </div>

      <RelationForm
        mode="edit"
        initialValues={initialValues}
        currentOwner={relation.owner}
        isPending={updateRelation.isPending}
        onSubmit={(values, form) => {
          updateRelation.mutate(toRelationUpdateRequest(values), {
            onError: (error) => {
              applyRelationFieldErrors(form, error, t);
            },
          });
        }}
      />

      <ContactsCard relationId={relation.id} />
      <RelationAttachmentsCard relationId={relation.id} />
      <OutreachesCard relationId={relation.id} />
      <RelationNotesCard notes={relation.notes} />
    </div>
  );
}

/**
 * The event the relation was met at, as a link to the event page. Until the
 * event is read, or when it cannot be read, the badge says "Event" without a
 * name, so the link stays and the event page explains what went wrong.
 */
function RelationEventBadge({ eventId }: { eventId: string }) {
  const t = useTranslations("relations.detail");
  const event = useEvent(eventId);
  const name = event.data?.data?.name;

  return (
    <Badge asChild variant="outline">
      <Link href={`/events/${eventId}`}>{name ? t("event", { name }) : t("eventUnnamed")}</Link>
    </Badge>
  );
}
