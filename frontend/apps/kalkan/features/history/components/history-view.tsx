"use client";

import { useCallback, useState, type ReactNode } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Card, CardAction, CardContent, CardDescription, CardHeader } from "@oba/ui/sky/card";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ToggleGroup, ToggleGroupItem } from "@oba/ui/sky/toggle-group";
import { usePage, usePageRevision, usePageRevisions } from "@/features/pages/hooks/use-pages";
import { pageErrorText } from "@/features/pages/lib/page-error";
import { contentLocaleOf } from "@/features/pages/lib/page-tree";
import type { PageDetail, PageRevisionListItem } from "@/features/pages/schemas";
import { DataTable } from "@/components/data-table";
import { pageHref } from "@/lib/routes";
import { useRevisionColumns } from "../hooks/use-revision-columns";
import { MarkdownSource, RevisionCompare } from "./revision-compare";

type ViewMode = "revision" | "compare";

/**
 * The history of a page: its revisions, newest first, and one of them below
 * the list, either as its Markdown source or beside the page's current text
 * in the same language. The newest revision is shown until another is picked.
 */
export function HistoryView({ pageId }: { pageId: string }) {
  const t = useTranslations("history");
  const tPages = useTranslations("pages");
  const tErrors = useTranslations("errors");
  const locale = useLocale();
  const page = usePage(pageId);
  const revisions = usePageRevisions(pageId);
  const [pickedId, setPickedId] = useState<string | null>(null);
  const select = useCallback((revision: PageRevisionListItem) => setPickedId(revision.id), []);
  const columns = useRevisionColumns({ onSelect: select });

  const rows = revisions.data?.data ?? [];
  const selectedId = pickedId ?? rows[0]?.id ?? null;
  const detail = page.data?.data;
  const preferred = contentLocaleOf(locale);
  const pageTitle =
    detail?.translations.find((translation) => translation.locale === preferred)?.title ??
    detail?.translations[0]?.title ??
    detail?.slug;

  let body: ReactNode;
  if (revisions.isError && revisions.data === undefined) {
    body = (
      <LoadError
        title={t("loadError")}
        message={pageErrorText(revisions.error, tPages, "view.loadError")}
        retryLabel={tErrors("tryAgain")}
        onRetry={() => void revisions.refetch()}
      />
    );
  } else if (revisions.data === undefined) {
    body = <TableLoading label={t("loading")} />;
  } else if (rows.length === 0) {
    body = <EmptyState title={t("empty")} description={t("emptyHint")} />;
  } else {
    body = (
      <>
        <DataTable<PageRevisionListItem>
          label={t("tableLabel")}
          columns={columns}
          data={rows}
          getRowId={(revision) => revision.id}
          onRowClick={select}
          selectedRowId={selectedId}
        />
        {selectedId && <RevisionPanel pageId={pageId} revisionId={selectedId} page={detail} />}
      </>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title={t("title")}
        description={pageTitle}
        back={{ href: pageHref(pageId), label: t("backToPage") }}
        linkComponent={Link}
      />
      {body}
    </div>
  );
}

/**
 * One revision: its Markdown read-only, or beside the current text of its
 * language. The compare waits for the page; a page without a text in that
 * language says so instead.
 */
function RevisionPanel({
  pageId,
  revisionId,
  page,
}: {
  pageId: string;
  revisionId: string;
  page: PageDetail | undefined;
}) {
  const t = useTranslations("history");
  const tPages = useTranslations("pages");
  const tErrors = useTranslations("errors");
  const { formatDateTime } = useDateFormat();
  const revision = usePageRevision(pageId, revisionId);
  const [mode, setMode] = useState<ViewMode>("revision");

  if (revision.isError && revision.data === undefined) {
    return (
      <LoadError
        title={t("revisionLoadError")}
        message={pageErrorText(revision.error, tPages, "view.loadError")}
        retryLabel={tErrors("tryAgain")}
        onRetry={() => void revision.refetch()}
      />
    );
  }
  const data = revision.data?.data;
  if (!data) {
    return <TableLoading label={t("revisionLoading")} />;
  }

  const current = page?.translations.find((translation) => translation.locale === data.locale);
  let content: ReactNode;
  if (mode === "revision") {
    content = <MarkdownSource body={data.body} />;
  } else if (!page) {
    content = <TableLoading label={tPages("view.loading")} />;
  } else if (!current) {
    content = <p className="text-muted-foreground text-sm">{t("noCurrent")}</p>;
  } else {
    content = <RevisionCompare revisionBody={data.body} currentBody={current.body} />;
  }

  return (
    <Card role="region" aria-labelledby="history-revision" className="gap-4">
      <CardHeader>
        <h2 id="history-revision" className="text-lg leading-none">
          {t("revisionHeading", { version: data.version, locale: t(`locales.${data.locale}`) })}
        </h2>
        <CardDescription>
          {t("revisionMeta", {
            when: formatDateTime(data.createdAt),
            name: data.createdByName ?? t("unknownUser"),
          })}
        </CardDescription>
        <CardAction>
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            value={mode}
            onValueChange={(value: string) => {
              if (value === "revision" || value === "compare") setMode(value);
            }}
            aria-label={t("modeLabel")}
          >
            <ToggleGroupItem value="revision">{t("modeRevision")}</ToggleGroupItem>
            <ToggleGroupItem value="compare">{t("modeCompare")}</ToggleGroupItem>
          </ToggleGroup>
        </CardAction>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <p className="text-foreground text-sm font-medium">{data.title}</p>
        {content}
      </CardContent>
    </Card>
  );
}
