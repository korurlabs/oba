"use client";

import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { parseAsInteger, useQueryState } from "nuqs";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable, TablePagination } from "@/components/data-table";
import { ActionEmptyState } from "@/components/page-states";
import { useRelationColumns } from "@/features/relations/hooks/use-relation-columns";
import { useRelations } from "@/features/relations/hooks/use-relations";
import { relationErrorText } from "@/features/relations/lib/relation-error";
import {
  RELATIONS_PAGE_SIZE,
  type RelationListItem,
  type RelationQueryRequest,
} from "@/features/relations/schemas";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useDeleteEvent, useEvent } from "../hooks/use-events";
import { eventErrorText } from "../lib/event-error";
import { formatEventDates, formatEventPlace } from "../lib/event-format";
import type { EventDetail } from "../schemas";
import { EventFormDialog } from "./event-form-dialog";

/**
 * One event: a heading with its name and type, the edit action for every
 * member and the delete action for an admin, a card with its details and
 * notes, and the relations linked to it.
 */
export function EventDetailView({ eventId }: { eventId: string }) {
  const t = useTranslations("events");
  const tErrors = useTranslations("errors");
  const event = useEvent(eventId);
  const data = event.data?.data;

  if (data) {
    return <EventDetailContent event={data} />;
  }

  if (event.isError) {
    if (isApiError(event.error, "OTK_NOT_009")) {
      return (
        <ActionEmptyState
          title={t("detail.notFoundTitle")}
          description={t("detail.notFoundDescription")}
          action={
            <Button asChild variant="outline">
              <Link href="/events">
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
        message={eventErrorText(event.error, t, "errors.loadHint")}
        retryLabel={tErrors("tryAgain")}
        onRetry={() => void event.refetch()}
      />
    );
  }

  return <TableLoading label={t("detail.loading")} />;
}

function EventDetailContent({ event }: { event: EventDetail }) {
  const t = useTranslations("events");
  const locale = useLocale();
  const router = useRouter();
  const { formatDate } = useDateFormat();
  const isAdmin = useIsOrganizationAdmin();
  const deleteEvent = useDeleteEvent();
  const [formOpen, setFormOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const countryNames = useMemo(() => new Intl.DisplayNames([locale], { type: "region" }), [locale]);
  const place = formatEventPlace(
    event.city,
    event.country,
    (code) => countryNames.of(code) ?? code
  );

  const handleDelete = () => {
    deleteEvent.mutate(event.id, {
      onSuccess: () => router.replace("/events"),
    });
  };

  return (
    <div className="flex flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="w-fit">
        <Link href="/events">
          <ArrowLeft aria-hidden="true" />
          {t("detail.back")}
        </Link>
      </Button>

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-2">
          <h1 className="break-words">{event.name}</h1>
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{t(`types.${event.type}`)}</Badge>
            <span className="text-muted-foreground text-sm tabular-nums">
              {t("detail.timestamps", {
                created: formatDate(event.createdAt),
                updated: formatDate(event.updatedAt),
              })}
            </span>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" onClick={() => setFormOpen(true)}>
            <Pencil aria-hidden="true" />
            {t("detail.edit")}
          </Button>
          {isAdmin && (
            <Button
              variant="outline"
              disabled={deleteEvent.isPending}
              onClick={() => setConfirmOpen(true)}
            >
              <Trash2 aria-hidden="true" />
              {deleteEvent.isPending ? t("detail.deleting") : t("detail.delete")}
            </Button>
          )}
        </div>
      </div>

      <Card role="region" aria-labelledby="event-details" className="gap-4">
        <CardHeader>
          <h2 id="event-details" className="text-lg leading-none">
            {t("detail.details")}
          </h2>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <dl className="grid gap-4 text-sm sm:grid-cols-3">
            <div className="flex flex-col gap-1">
              <dt className="text-muted-foreground">{t("detail.dates")}</dt>
              <dd className="tabular-nums">
                {formatEventDates(event.startDate, event.endDate, locale)}
              </dd>
            </div>
            <div className="flex flex-col gap-1">
              <dt className="text-muted-foreground">{t("detail.place")}</dt>
              <dd className="break-words">
                {place ?? <span className="text-muted-foreground">{t("noPlace")}</span>}
              </dd>
            </div>
            <div className="flex min-w-0 flex-col gap-1">
              <dt className="text-muted-foreground">{t("detail.website")}</dt>
              <dd className="truncate">
                {event.website ? (
                  <a
                    href={event.website}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:underline"
                    title={event.website}
                  >
                    {event.website.replace(/^https?:\/\//, "")}
                  </a>
                ) : (
                  <span className="text-muted-foreground">{t("noWebsite")}</span>
                )}
              </dd>
            </div>
          </dl>
          <div className="flex flex-col gap-1 text-sm">
            <h3 className="text-muted-foreground text-sm font-normal">{t("detail.notes")}</h3>
            {event.notes ? (
              <p className="break-words whitespace-pre-wrap">{event.notes}</p>
            ) : (
              <p className="text-muted-foreground">{t("detail.noNotes")}</p>
            )}
          </div>
        </CardContent>
      </Card>

      <EventRelationsCard eventId={event.id} />

      <EventFormDialog eventId={event.id} open={formOpen} onOpenChange={setFormOpen} />
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t("deleteTitle")}
        description={t("deleteConfirm", { name: event.name })}
        confirmLabel={t("deleteAction")}
        cancelLabel={t("form.cancel")}
        destructive
        onConfirm={() => {
          handleDelete();
          setConfirmOpen(false);
        }}
      />
    </div>
  );
}

/**
 * The relations linked to the event, of every type and stage, newest change
 * first, with their page in the URL.
 */
function EventRelationsCard({ eventId }: { eventId: string }) {
  const t = useTranslations("events.relationsCard");
  const tRelations = useTranslations("relations");
  const tErrors = useTranslations("errors");
  const router = useRouter();
  const [page, setPage] = useQueryState("page", parseAsInteger.withDefault(1));
  const query = useMemo(
    (): RelationQueryRequest => ({
      eventId,
      page: Math.max(1, page),
      amount: RELATIONS_PAGE_SIZE,
    }),
    [eventId, page]
  );
  const relations = useRelations(query);
  const columns = useRelationColumns();
  const rows = useMemo(
    (): RelationListItem[] => relations.data?.data ?? [],
    [relations.data?.data]
  );
  const pagination = relations.data?.pagination;

  return (
    <Card role="region" aria-labelledby="event-relations" className="gap-4">
      <CardHeader>
        <h2 id="event-relations" className="text-lg leading-none">
          {t("title")}
        </h2>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {relations.isError && relations.data === undefined ? (
          <LoadError
            title={t("loadError")}
            message={relationErrorText(relations.error, tRelations, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void relations.refetch()}
          />
        ) : relations.data === undefined ? (
          <TableLoading label={t("loading")} />
        ) : rows.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t("empty")}</p>
        ) : (
          <>
            <DataTable<RelationListItem>
              label={t("tableLabel")}
              columns={columns}
              data={rows}
              isFetching={relations.isFetching && relations.isPlaceholderData}
              getRowId={(relation) => relation.id}
              onRowClick={(relation) => router.push(`/relations/${relation.id}`)}
            />
            {pagination && pagination.totalPages > 1 && (
              <TablePagination
                page={pagination.currentPage}
                totalPages={pagination.totalPages}
                onPageChange={(next) => void setPage(next)}
                label={tRelations("pagination.label")}
                previousLabel={tRelations("pagination.previous")}
                nextLabel={tRelations("pagination.next")}
                summary={tRelations("pagination.summary", {
                  page: pagination.currentPage,
                  pages: pagination.totalPages,
                  total: pagination.totalElements,
                })}
              />
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
