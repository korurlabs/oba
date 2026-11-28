"use client";

import { useState, type ReactNode } from "react";
import { useLocale, useTranslations } from "next-intl";
import { parseAsString, useQueryState } from "nuqs";
import { Plus, RotateCcw } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent } from "@oba/ui/sky/card";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import { useRestorePage, useTrash } from "@/features/pages/hooks/use-pages";
import { useSectionRoot, useSetUpSection } from "@/features/pages/hooks/use-section-root";
import { pageErrorText } from "@/features/pages/lib/page-error";
import { isLevelAtLeast, KalkanLevel, type PageTrashItem } from "@/features/pages/schemas";
import { useIsOrganizationAdmin, useIsOrganizationMember } from "@/hooks/use-is-organization-admin";
import { newPageHref } from "@/lib/routes";
import type { SectionSlug } from "@/lib/sections";
import { visiblePageIds } from "../lib/tree-search";
import { SectionTree } from "./section-tree";

/**
 * A section: the page tree under its root page. A section whose root does
 * not exist yet offers an admin to set it up; anyone else is told to ask one.
 * A root in the trash is not missing: setting the section up again would make
 * a second root, so a member whose trash holds it gets its restore instead.
 * With EDIT on the section the tree can be rearranged and pages added. The
 * search narrows the tree in the browser, since the tree arrives whole.
 */
export function SectionView({ slug }: { slug: SectionSlug }) {
  const t = useTranslations("sections");
  const tTree = useTranslations("sectionTree");
  const tSidebar = useTranslations("sidebar");
  const tPages = useTranslations("pages");
  const locale = useLocale();
  const [search, setSearch] = useQueryState("search", parseAsString);
  const [searchReset, setSearchReset] = useState(0);
  const isAdmin = useIsOrganizationAdmin();
  const isMember = useIsOrganizationMember();
  const { section, data, isPending, isError, error, refetch } = useSectionRoot(slug);
  const setUp = useSetUpSection();
  // The trash lists only the deletes the caller may undo, so a root found
  // there is one the caller may restore. A GUEST is refused the trash.
  const trash = useTrash({ enabled: isMember && section !== undefined && !section.root });
  const title = tSidebar(`sections.${slug}`);
  const root = section?.root ?? null;
  // The server refuses a GUEST every write whatever its level on the root.
  const canEdit = isMember && isLevelAtLeast(root?.level, KalkanLevel.EDIT);
  const deletedRoot = deletedRootOf(trash.data?.data ?? [], slug);
  const pages = data?.data ?? [];
  const visibleIds = root && search ? visiblePageIds(pages, root.id, search, locale) : undefined;
  const clearSearch = () => {
    void setSearch(null);
    setSearchReset((count) => count + 1);
  };

  let body: ReactNode;
  if (isPending) {
    body = <TableLoading label={t("loading")} />;
  } else if (isError || !section) {
    body = (
      <LoadError
        title={t("loadError")}
        message={pageErrorText(error, tPages, "view.loadError")}
        onRetry={() => void refetch()}
        retryLabel={tPages("view.retry")}
      />
    );
  } else if (!root && isMember && trash.isError) {
    body = (
      <LoadError
        title={t("inTrash.loadError")}
        message={pageErrorText(trash.error, tPages, "view.loadError")}
        onRetry={() => void trash.refetch()}
        retryLabel={tPages("view.retry")}
      />
    );
  } else if (!root && isMember && trash.data === undefined) {
    body = <TableLoading label={t("loading")} />;
  } else if (!root && deletedRoot) {
    body = <SectionInTrash root={deletedRoot} />;
  } else if (!root && isAdmin) {
    body = (
      <EmptyState
        title={t("notSetUp.title")}
        description={t("notSetUp.adminDescription")}
        action={
          <Button onClick={() => setUp.mutate(slug)} disabled={setUp.isPending}>
            {t("notSetUp.action")}
          </Button>
        }
      />
    );
  } else if (!root) {
    body = <EmptyState title={t("notSetUp.title")} description={t("notSetUp.description")} />;
  } else if (section.children.length === 0 || visibleIds?.size === 0) {
    body = (
      <ListEmptyState
        filtered={section.children.length > 0}
        onClearFilters={clearSearch}
        create={canEdit ? <NewPageButton parentId={root.id} /> : undefined}
        labels={{
          emptyTitle: tTree("empty.title"),
          emptyDescription: canEdit
            ? tTree("empty.editorDescription")
            : tTree("empty.readerDescription"),
          filteredTitle: tTree("filtered.title"),
          filteredDescription: tTree("filtered.description"),
          clearFilters: tTree("filtered.clear"),
        }}
      />
    );
  } else {
    body = (
      <Card className="py-4">
        <CardContent className="px-3">
          <SectionTree
            pages={pages}
            root={root}
            sectionTitle={title}
            canEdit={canEdit}
            visibleIds={visibleIds}
          />
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={title} />
      {root && (
        <ListToolbar
          search={
            <SearchInput
              value={search}
              onSearchChange={(next) => void setSearch(next ?? null)}
              label={tTree("search")}
              clearLabel={tTree("searchClear")}
              resetSignal={searchReset}
            />
          }
          create={canEdit && <NewPageButton parentId={root.id} />}
        />
      )}
      {body}
    </div>
  );
}

/** The trash entry of the section's root: its path is the section's slug, compared without case. */
function deletedRootOf(
  trash: readonly PageTrashItem[],
  slug: SectionSlug
): PageTrashItem | undefined {
  return trash.find((item) => item.path.toLowerCase() === slug.toLowerCase());
}

/**
 * A section whose root page is in the caller's trash: when it was deleted,
 * and its restore. A restore brings back the pages the
 * delete took, so the section returns as it was.
 */
function SectionInTrash({ root }: { root: PageTrashItem }) {
  const t = useTranslations("sections.inTrash");
  const { formatDate } = useDateFormat();
  const restore = useRestorePage();
  const date = formatDate(root.deletedAt);

  return (
    <EmptyState
      title={t("title")}
      description={
        root.deletedByName
          ? t("description", { date, name: root.deletedByName })
          : t("descriptionUnknown", { date })
      }
      action={
        <Button onClick={() => restore.mutate(root.id)} disabled={restore.isPending}>
          <RotateCcw aria-hidden="true" />
          {t("restore")}
        </Button>
      }
    />
  );
}

/** Opens the page form with the given parent; the form is its own screen. */
function NewPageButton({ parentId }: { parentId: string }) {
  const t = useTranslations("sectionTree");
  return (
    <Button asChild>
      <Link href={newPageHref(parentId)}>
        <Plus aria-hidden="true" />
        {t("newPage")}
      </Link>
    </Button>
  );
}
