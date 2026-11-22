"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { RotateCcw } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Alert, AlertDescription } from "@oba/ui/sky/alert";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { LoadError } from "@oba/ui/sky/page-states";
import { useRestorePage, useTrash } from "@/features/pages/hooks/use-pages";
import { pageErrorText } from "@/features/pages/lib/page-error";
import { deletedPagesOf } from "../lib/tree-moves";

/**
 * The pages of a section that are in the trash, each with its restore. The
 * trash lists only deletes the caller may undo, so the card hides itself
 * when the section has none. A restore under a page that is still deleted is
 * refused (`KLK_STA_003`); the card then says to restore the parent first.
 */
export function DeletedPagesCard({ rootSlug }: { rootSlug: string }) {
  const t = useTranslations("sectionTree.deleted");
  const tPages = useTranslations("pages");
  const { formatDate } = useDateFormat();
  const trash = useTrash();
  const restore = useRestorePage();
  const [blockedPath, setBlockedPath] = useState<string | null>(null);

  if (trash.isError && trash.data === undefined) {
    return (
      <LoadError
        title={t("loadError")}
        message={pageErrorText(trash.error, tPages, "view.loadError")}
        retryLabel={tPages("view.retry")}
        onRetry={() => void trash.refetch()}
      />
    );
  }
  // Most sections have nothing deleted, so the card stays out of the way
  // while the trash loads instead of showing an outline that then vanishes.
  if (trash.data === undefined) return null;

  const items = deletedPagesOf(trash.data.data ?? [], rootSlug);
  if (items.length === 0) return null;

  return (
    <Card role="region" aria-labelledby="deleted-pages" className="gap-4">
      <CardHeader>
        <CardTitle id="deleted-pages" className="text-lg">
          {t("title")}
        </CardTitle>
        <CardDescription>{t("description")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {blockedPath !== null && (
          <Alert variant="destructive">
            <AlertDescription>{t("parentFirst", { path: blockedPath })}</AlertDescription>
          </Alert>
        )}
        <ul className="flex flex-col gap-2" aria-label={t("title")}>
          {items.map((item) => {
            const title = item.title ?? t("untitled");
            const date = formatDate(item.deletedAt);
            return (
              <li
                key={item.id}
                className="bg-card flex flex-wrap items-center gap-3 rounded-lg border p-2"
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{title}</p>
                  <p className="text-muted-foreground text-xs">
                    <span className="font-mono">{item.path}</span>
                    {" · "}
                    {item.deletedByName
                      ? t("meta", { date, name: item.deletedByName })
                      : t("metaUnknown", { date })}
                    {" · "}
                    {t("descendants", { count: item.descendantCount })}
                  </p>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  aria-label={t("restoreLabel", { title })}
                  disabled={restore.isPending}
                  onClick={() => {
                    setBlockedPath(null);
                    restore.mutate(item.id, {
                      onError: (error: unknown) => {
                        if (isApiError(error, "KLK_STA_003")) setBlockedPath(item.path);
                      },
                    });
                  }}
                >
                  <RotateCcw aria-hidden="true" />
                  {t("restore")}
                </Button>
              </li>
            );
          })}
        </ul>
      </CardContent>
    </Card>
  );
}
