"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus, X } from "lucide-react";
import { todayDateOnly } from "@oba/shared/lib/date-only";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { Tabs, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable, TablePagination } from "@/components/data-table";
import { ActionEmptyState, PageHeading } from "@/components/page-states";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useEventColumns } from "../hooks/use-event-columns";
import { useEventFilterParams } from "../hooks/use-event-filter-params";
import { useDeleteEvent, useEvents } from "../hooks/use-events";
import { eventErrorText } from "../lib/event-error";
import { EVENT_TABS, toEventQuery, type EventTab } from "../lib/event-filters";
import type { EventListItem } from "../schemas";
import { EventFormDialog } from "./event-form-dialog";

function isEventTab(value: string): value is EventTab {
  return (EVENT_TABS as readonly string[]).includes(value);
}

/**
 * The organization's events in an Upcoming and a Past tab, with a name
 * search and paging in the URL. Upcoming and past are judged on the viewer's
 * calendar day, which the list sends as `today`. Every member creates and
 * edits events in a dialog; only an admin deletes.
 */
export function EventsView() {
  const t = useTranslations("events");
  const tErrors = useTranslations("errors");
  const router = useRouter();
  const isAdmin = useIsOrganizationAdmin();
  const [filters, setFilters] = useEventFilterParams();
  // Read per change of the URL state, so a list opened before midnight moves
  // on to the new day with the next tab, search or page.
  const query = useMemo(() => toEventQuery(filters, todayDateOnly()), [filters]);
  const events = useEvents(query);
  const deleteEvent = useDeleteEvent();
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<EventListItem | null>(null);

  const handleNew = useCallback(() => {
    setEditingId(null);
    setFormOpen(true);
  }, []);
  const handleEdit = useCallback((event: EventListItem) => {
    setEditingId(event.id);
    setFormOpen(true);
  }, []);
  const handleDelete = useCallback((event: EventListItem) => setDeleting(event), []);
  const columns = useEventColumns({
    onEdit: handleEdit,
    onDelete: isAdmin ? handleDelete : null,
  });
  const handleTabChange = useCallback(
    (value: string) => {
      if (!isEventTab(value)) return;
      void setFilters({ tab: value, page: 1 });
    },
    [setFilters]
  );

  const rows = useMemo((): EventListItem[] => events.data?.data ?? [], [events.data?.data]);
  const pagination = events.data?.pagination;
  const isFiltered = !!filters.search;

  const newButton = (
    <Button onClick={handleNew}>
      <Plus aria-hidden="true" />
      {t("new")}
    </Button>
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} />

      <ListToolbar
        tabs={
          <Tabs value={filters.tab} onValueChange={handleTabChange}>
            <TabsList aria-label={t("tabsLabel")}>
              {EVENT_TABS.map((tab) => (
                <TabsTrigger key={tab} value={tab}>
                  {t(`tabs.${tab}`)}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        }
        search={
          <SenseySearchInput
            value={filters.search}
            placeholder={t("filters.searchPlaceholder")}
            onSearchChange={(search) => void setFilters({ search: search ?? null, page: 1 })}
            className="w-40 sm:w-56"
          />
        }
        create={newButton}
      />

      {events.isError && events.data === undefined ? (
        <LoadError
          title={t("loadError")}
          message={eventErrorText(events.error, t, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void events.refetch()}
        />
      ) : events.data === undefined ? (
        <TableLoading label={t("loading")} />
      ) : rows.length === 0 ? (
        isFiltered ? (
          <ActionEmptyState
            title={t("emptyFiltered")}
            description={t("emptyFilteredHint")}
            action={
              <Button variant="outline" onClick={() => void setFilters({ search: null, page: 1 })}>
                <X aria-hidden="true" />
                {t("filters.clear")}
              </Button>
            }
          />
        ) : (
          <ActionEmptyState
            title={t(`emptyByTab.${filters.tab}`)}
            description={t(`emptyHintByTab.${filters.tab}`)}
            action={newButton}
          />
        )
      ) : (
        <>
          {events.isError && (
            <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {t("reloadError")}
            </p>
          )}
          <DataTable<EventListItem>
            label={t("tableLabel")}
            columns={columns}
            data={rows}
            isFetching={events.isFetching && events.isPlaceholderData}
            getRowId={(event) => event.id}
            onRowClick={(event) => router.push(`/events/${event.id}`)}
          />
          {pagination && pagination.totalPages > 1 && (
            <TablePagination
              page={pagination.currentPage}
              totalPages={pagination.totalPages}
              onPageChange={(page) => void setFilters({ page })}
              label={t("pagination.label")}
              previousLabel={t("pagination.previous")}
              nextLabel={t("pagination.next")}
              summary={t("pagination.summary", {
                page: pagination.currentPage,
                pages: pagination.totalPages,
                total: pagination.totalElements,
              })}
            />
          )}
        </>
      )}

      <EventFormDialog eventId={editingId} open={formOpen} onOpenChange={setFormOpen} />
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t("deleteTitle")}
        description={t("deleteConfirm", { name: deleting?.name ?? "" })}
        confirmLabel={t("deleteAction")}
        cancelLabel={t("form.cancel")}
        destructive
        onConfirm={() => {
          if (deleting) deleteEvent.mutate(deleting.id);
          setDeleting(null);
        }}
      />
    </div>
  );
}
