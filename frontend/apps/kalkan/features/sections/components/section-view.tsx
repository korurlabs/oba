"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { FilePlus, FileText } from "lucide-react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent } from "@oba/ui/sky/card";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ActionEmptyState, PageHeading } from "@/components/page-states";
import { useSectionRoot, useSetUpSection } from "@/features/pages/hooks/use-section-root";
import { pageErrorText } from "@/features/pages/lib/page-error";
import { isLevelAtLeast, KalkanLevel } from "@/features/pages/schemas";
import { useIsOrganizationAdmin, useIsOrganizationMember } from "@/hooks/use-is-organization-admin";
import { NEW_PAGE_HREF, pageHref } from "@/lib/routes";
import type { SectionSlug } from "@/lib/sections";
import { DeletedPagesCard } from "./deleted-pages-card";
import { SectionTree } from "./section-tree";

/**
 * A section: the page tree under its root page. A section whose root does
 * not exist yet offers an admin to set it up; anyone else is told to ask one.
 * With EDIT on the section the tree can be rearranged and pages added; a
 * member also sees the section's deleted pages with their restore.
 */
export function SectionView({ slug }: { slug: SectionSlug }) {
  const t = useTranslations("sections");
  const tTree = useTranslations("sectionTree");
  const tSidebar = useTranslations("sidebar");
  const tPages = useTranslations("pages");
  const isAdmin = useIsOrganizationAdmin();
  const isMember = useIsOrganizationMember();
  const { section, data, isPending, isError, error, refetch } = useSectionRoot(slug);
  const setUp = useSetUpSection();
  const title = tSidebar(`sections.${slug}`);
  const root = section?.root ?? null;
  // The server refuses a GUEST every write whatever its level on the root.
  const canEdit = isMember && isLevelAtLeast(root?.level, KalkanLevel.EDIT);

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
  } else if (!root && isAdmin) {
    body = (
      <ActionEmptyState
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
  } else if (section.children.length === 0 && canEdit) {
    body = (
      <ActionEmptyState
        title={tTree("empty.title")}
        description={tTree("empty.editorDescription")}
        action={<NewPageButton parentId={root.id} />}
      />
    );
  } else if (section.children.length === 0) {
    body = (
      <EmptyState title={tTree("empty.title")} description={tTree("empty.readerDescription")} />
    );
  } else {
    body = (
      <Card className="py-4">
        <CardContent className="px-3">
          <SectionTree
            pages={data?.data ?? []}
            root={root}
            sectionTitle={title}
            canEdit={canEdit}
          />
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title={title}
        actions={
          root && (
            <>
              <Button asChild variant="outline">
                <Link href={pageHref(root.id)}>
                  <FileText aria-hidden="true" />
                  {tTree("sectionPage")}
                </Link>
              </Button>
              {canEdit && <NewPageButton parentId={root.id} />}
            </>
          )
        }
      />
      {body}
      {root && isMember && <DeletedPagesCard rootSlug={root.slug} />}
    </div>
  );
}

/** Opens the page form with the given parent; the form is its own screen. */
function NewPageButton({ parentId }: { parentId: string }) {
  const t = useTranslations("sectionTree");
  return (
    <Button asChild>
      <Link href={{ pathname: NEW_PAGE_HREF, query: { parent: parentId } }}>
        <FilePlus aria-hidden="true" />
        {t("newPage")}
      </Link>
    </Button>
  );
}
