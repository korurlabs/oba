"use client";

import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { AlertTriangle, ArrowLeft, RefreshCw } from "lucide-react";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Alert, AlertDescription, AlertTitle } from "@oba/ui/sky/alert";
import { Button } from "@oba/ui/sky/button";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ActionEmptyState, PageHeading } from "@/components/page-states";
import { pageHref } from "@/lib/routes";
import { usePage, useUpdatePage } from "../hooks/use-pages";
import { pageErrorText } from "../lib/page-error";
import { contentLocaleOf } from "../lib/page-tree";
import { isLevelAtLeast, KalkanLevel, type PageDetail } from "../schemas";
import {
  isVersionConflict,
  pageFormValuesFromDetail,
  storedLocales,
  toPageUpdateRequest,
} from "../schemas/page-form";
import { applyPageFieldErrors } from "./page-field-errors";
import { PageForm } from "./page-form";

/**
 * The edit screen of a page. The form edits the version it loaded and saves
 * against it: when someone else saved the page meanwhile, the save is refused
 * and the screen says so, keeps the user's text and offers to load the newer
 * version. A background refresh of the page never replaces the form.
 */
export function PageEditView({ pageId }: { pageId: string }) {
  const t = useTranslations("pageForm");
  const tPages = useTranslations("pages");
  const locale = useLocale();
  const router = useRouter();
  const page = usePage(pageId);
  const updatePage = useUpdatePage();
  // The version being edited. Set from the first load, then only by a save or
  // an explicit reload, so a refetch in the background leaves the form alone.
  const [base, setBase] = useState<PageDetail | null>(null);
  const [conflict, setConflict] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [reloadError, setReloadError] = useState<string | null>(null);

  const loaded = page.data?.data ?? null;
  if (base === null && loaded !== null) {
    setBase(loaded);
  }

  const initialValues = useMemo(() => (base ? pageFormValuesFromDetail(base) : null), [base]);
  const requiredLocales = useMemo(() => (base ? storedLocales(base) : []), [base]);

  const back = (
    <Button asChild variant="ghost" size="sm" className="w-fit">
      <Link href={pageHref(pageId)}>
        <ArrowLeft aria-hidden="true" />
        {t("backToPage")}
      </Link>
    </Button>
  );

  if (base === null || initialValues === null) {
    if (page.isError) {
      return (
        <LoadError
          title={tPages("view.loadError")}
          message={pageErrorText(page.error, tPages, "view.loadError")}
          onRetry={() => void page.refetch()}
          retryLabel={t("retry")}
        />
      );
    }
    return <TableLoading label={tPages("view.loading")} />;
  }

  if (!isLevelAtLeast(base.level, KalkanLevel.EDIT)) {
    return (
      <div className="flex flex-col gap-6">
        {back}
        <ActionEmptyState
          title={t("readOnly.title")}
          description={t("readOnly.description")}
          action={
            <Button asChild variant="outline">
              <Link href={pageHref(pageId)}>{t("backToPage")}</Link>
            </Button>
          }
        />
      </div>
    );
  }

  const reload = async () => {
    setReloading(true);
    setReloadError(null);
    try {
      const result = await page.refetch({ throwOnError: true });
      const latest = result.data?.data;
      if (latest) {
        setBase(latest);
        setConflict(false);
      }
    } catch (error: unknown) {
      // The conflict notice stays, so the user can try the reload again.
      console.warn("[page-edit] reload after a version conflict failed", error);
      setReloadError(pageErrorText(error, tPages, "view.loadError"));
    } finally {
      setReloading(false);
    }
  };

  const title =
    base.translations.find((translation) => translation.locale === contentLocaleOf(locale))
      ?.title ??
    base.translations[0]?.title ??
    base.slug;

  return (
    <div className="flex flex-col gap-6">
      {back}
      <PageHeading title={t("editTitle", { title })} description={base.path} />
      <PageForm
        mode="edit"
        initialValues={initialValues}
        requiredLocales={requiredLocales}
        initialLocale={contentLocaleOf(locale)}
        pageId={pageId}
        isPending={updatePage.isPending || updatePage.isSuccess || reloading}
        notice={
          conflict ? (
            <VersionConflictNotice
              reloading={reloading}
              error={reloadError}
              onReload={() => void reload()}
            />
          ) : null
        }
        onSubmit={(values, form) => {
          updatePage.mutate(
            { pageId, data: toPageUpdateRequest(values, base.version) },
            {
              onSuccess: () => {
                router.push(pageHref(pageId));
              },
              onError: (error) => {
                if (isVersionConflict(error)) {
                  setConflict(true);
                  setReloadError(null);
                  return;
                }
                applyPageFieldErrors(form, error, tPages);
              },
            }
          );
        }}
        secondaryAction={
          <Button asChild variant="outline">
            <Link href={pageHref(pageId)}>{t("cancel")}</Link>
          </Button>
        }
      />
    </div>
  );
}

/**
 * Someone else saved the page after this editor loaded it. Reloading puts
 * the newer version in the form and drops the user's unsaved text, so the
 * notice says that before the user chooses.
 */
function VersionConflictNotice({
  reloading,
  error,
  onReload,
}: {
  reloading: boolean;
  error: string | null;
  onReload: () => void;
}) {
  const t = useTranslations("pageForm.conflict");

  return (
    <Alert variant="destructive">
      <AlertTriangle aria-hidden="true" />
      <AlertTitle>{t("title")}</AlertTitle>
      <AlertDescription>
        <p>{t("description")}</p>
        {error ? <p>{error}</p> : null}
        <Button type="button" variant="outline" size="sm" disabled={reloading} onClick={onReload}>
          <RefreshCw aria-hidden="true" />
          {reloading ? t("reloading") : t("reload")}
        </Button>
      </AlertDescription>
    </Alert>
  );
}
