"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { ArrowLeft, FilePlus, History, Pencil, RotateCcw, ShieldCheck, Trash2 } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Alert, AlertDescription } from "@oba/ui/sky/alert";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent } from "@oba/ui/sky/card";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ToggleGroup, ToggleGroupItem } from "@oba/ui/sky/toggle-group";
import { ActionEmptyState } from "@/components/page-states";
import { descendantCountOf } from "@/features/sections/lib/tree-moves";
import { useIsOrganizationMember } from "@/hooks/use-is-organization-admin";
import {
  newPageHref,
  pageEditHref,
  pageHistoryHref,
  pageHref,
  pagePermissionsHref,
  sectionHref,
} from "@/lib/routes";
import { isSectionSlug } from "@/lib/sections";
import { useDeletePage, usePage, usePageTree, useRestorePage, useTrash } from "../hooks/use-pages";
import { pageErrorText } from "../lib/page-error";
import { contentLocaleOf } from "../lib/page-tree";
import {
  isLevelAtLeast,
  KalkanLevel,
  KalkanLocaleSchema,
  type KalkanLocale,
  type PageDetail,
} from "../schemas";
import { DeletePageDialog } from "./delete-page-dialog";
import { PageAttachmentsCard } from "./page-attachments-card";
import { PageBreadcrumbs } from "./page-breadcrumbs";
import { PageMarkdownBody } from "./page-markdown-body";

/**
 * One page, read-only: its place in the tree, its title and body in the
 * content language that fits the interface (the other one when the page has
 * no such translation), its compliance mark and its attachments. EDIT on the
 * section adds edit, a new subpage and delete. A page that answers 404 may be
 * in the trash; a member who can restore it gets the restore here.
 */
export function PageView({ pageId }: { pageId: string }) {
  const t = useTranslations("pages");
  const tView = useTranslations("pageView");
  const page = usePage(pageId);
  const data = page.data?.data;

  if (data) {
    return <PageContent page={data} />;
  }

  if (page.isError) {
    if (isApiError(page.error, "KLK_NOT_001")) {
      return <PageNotFound pageId={pageId} />;
    }
    if (isApiError(page.error, "KLK_SEC_001")) {
      return (
        <ActionEmptyState
          title={tView("noAccess.title")}
          description={tView("noAccess.description")}
          action={<BackButton />}
        />
      );
    }
    return (
      <LoadError
        title={t("view.loadError")}
        message={pageErrorText(page.error, t, "view.loadError")}
        onRetry={() => void page.refetch()}
        retryLabel={t("view.retry")}
      />
    );
  }

  return <TableLoading label={t("view.loading")} />;
}

function BackButton() {
  const t = useTranslations("pageView");
  return (
    <Button asChild variant="outline">
      <Link href="/">
        <ArrowLeft aria-hidden="true" />
        {t("back")}
      </Link>
    </Button>
  );
}

/**
 * A page the server does not show: deleted, never there, or out of the
 * caller's reach, which the server answers alike. A member's trash holds the
 * deletes they may undo; when the page is one, it can be restored from here.
 */
function PageNotFound({ pageId }: { pageId: string }) {
  const t = useTranslations("pageView");
  const { formatDate } = useDateFormat();
  const isMember = useIsOrganizationMember();
  const trash = useTrash({ enabled: isMember });
  const restore = useRestorePage();
  const deleted = trash.data?.data?.find((item) => item.id === pageId);

  if (deleted) {
    const parentDeleted = isApiError(restore.error, "KLK_STA_003");
    return (
      <div className="flex flex-col gap-4">
        {parentDeleted && (
          <Alert variant="destructive">
            <AlertDescription>{t("deleted.parentFirst")}</AlertDescription>
          </Alert>
        )}
        <ActionEmptyState
          title={t("deleted.title")}
          description={t("deleted.description", { date: formatDate(deleted.deletedAt) })}
          action={
            <Button onClick={() => restore.mutate(pageId)} disabled={restore.isPending}>
              <RotateCcw aria-hidden="true" />
              {t("deleted.restore")}
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <ActionEmptyState
      title={t("notFound.title")}
      description={t("notFound.description")}
      action={<BackButton />}
    />
  );
}

/** The translation shown first: the one that fits the interface, else the other one. */
function initialLocale(page: PageDetail, uiLocale: string): KalkanLocale | undefined {
  const preferred = contentLocaleOf(uiLocale);
  if (page.translations.some((translation) => translation.locale === preferred)) return preferred;
  return page.translations[0]?.locale;
}

function PageContent({ page }: { page: PageDetail }) {
  const t = useTranslations("pageView");
  const locale = useLocale();
  const router = useRouter();
  const { formatDate } = useDateFormat();
  const isMember = useIsOrganizationMember();
  const tree = usePageTree();
  const deletePage = useDeletePage();
  const [chosen, setChosen] = useState<KalkanLocale | undefined>(undefined);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const pages = tree.data?.data ?? [];
  const preferred = contentLocaleOf(locale);
  const shownLocale =
    chosen && page.translations.some((translation) => translation.locale === chosen)
      ? chosen
      : initialLocale(page, locale);
  const translation = page.translations.find((candidate) => candidate.locale === shownLocale);
  const title = translation?.title ?? page.slug;
  const hasPreferred = page.translations.some((candidate) => candidate.locale === preferred);

  // The server refuses a GUEST every write; deleting a section's root takes MANAGE.
  const canEdit = isMember && isLevelAtLeast(page.level, KalkanLevel.EDIT);
  // Access is set on a section's root, by whoever MANAGEs it.
  const canManageAccess =
    isMember && page.parentId === null && isLevelAtLeast(page.level, KalkanLevel.MANAGE);
  const canSeeHistory = isLevelAtLeast(page.level, KalkanLevel.VIEW);
  const canDelete =
    isMember &&
    isLevelAtLeast(page.level, page.parentId === null ? KalkanLevel.MANAGE : KalkanLevel.EDIT);

  // After a delete the reader goes back to the section, else to the parent page.
  const rootSlug = pages
    .find((candidate) => candidate.id === (page.rootId ?? page.id))
    ?.slug.toLowerCase();
  const afterDeleteHref =
    page.parentId === null
      ? "/"
      : rootSlug && isSectionSlug(rootSlug)
        ? sectionHref(rootSlug)
        : pageHref(page.parentId);

  const confirmDelete = () => {
    deletePage.mutate(page.id, {
      onSuccess: () => {
        setDeleteOpen(false);
        router.replace(afterDeleteHref);
      },
    });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageBreadcrumbs page={page} title={title} pages={pages} />

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-2">
          <h1 className="break-words">{title}</h1>
          <div className="flex flex-wrap items-center gap-2">
            {page.compliance && <Badge variant="info">{t("compliance")}</Badge>}
            {translation && (
              <span className="text-muted-foreground text-sm tabular-nums">
                {translation.updatedByName
                  ? t("updated", {
                      date: formatDate(translation.updatedAt),
                      name: translation.updatedByName,
                    })
                  : t("updatedUnknown", { date: formatDate(translation.updatedAt) })}
              </span>
            )}
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            aria-label={t("language")}
            value={shownLocale ?? ""}
            onValueChange={(value: string) => {
              const parsed = KalkanLocaleSchema.safeParse(value);
              if (parsed.success) setChosen(parsed.data);
            }}
          >
            {KalkanLocaleSchema.options.map((option) => (
              <ToggleGroupItem
                key={option}
                value={option}
                disabled={!page.translations.some((candidate) => candidate.locale === option)}
              >
                {option}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
          {canSeeHistory && (
            <Button asChild variant="outline">
              <Link href={pageHistoryHref(page.id)}>
                <History aria-hidden="true" />
                {t("history")}
              </Link>
            </Button>
          )}
          {canManageAccess && (
            <Button asChild variant="outline">
              <Link href={pagePermissionsHref(page.id)}>
                <ShieldCheck aria-hidden="true" />
                {t("permissions")}
              </Link>
            </Button>
          )}
          {canEdit && (
            <>
              <Button asChild variant="outline">
                <Link href={newPageHref(page.id)}>
                  <FilePlus aria-hidden="true" />
                  {t("newChild")}
                </Link>
              </Button>
              <Button asChild>
                <Link href={pageEditHref(page.id)}>
                  <Pencil aria-hidden="true" />
                  {t("edit")}
                </Link>
              </Button>
            </>
          )}
          {canDelete && (
            // Outlined, not filled: the heading keeps one filled action, and this one asks first.
            <Button
              variant="outline"
              className="text-destructive hover:text-destructive"
              onClick={() => setDeleteOpen(true)}
              disabled={deletePage.isPending}
            >
              <Trash2 aria-hidden="true" />
              {t("delete")}
            </Button>
          )}
        </div>
      </div>

      {!hasPreferred && shownLocale && (
        <p className="text-muted-foreground text-sm" role="note">
          {t("fallback", {
            wanted: t(`locales.${preferred}`),
            shown: t(`locales.${shownLocale}`),
          })}
        </p>
      )}

      <Card className="py-4">
        <CardContent className="px-3">
          {translation && translation.body.trim() !== "" ? (
            <PageMarkdownBody key={shownLocale} markdown={translation.body} label={t("body")} />
          ) : (
            <p className="text-muted-foreground px-3 py-2 text-sm">{t("emptyBody")}</p>
          )}
        </CardContent>
      </Card>

      <PageAttachmentsCard pageId={page.id} />

      <DeletePageDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title={title}
        descendantCount={descendantCountOf(pages, page.id)}
        isDeleting={deletePage.isPending}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
