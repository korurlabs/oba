"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@oba/ui/sky/button";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ActionEmptyState, PageHeading } from "@/components/page-states";
import { useSectionRoot, useSetUpSection } from "@/features/pages/hooks/use-section-root";
import { pageErrorText } from "@/features/pages/lib/page-error";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import type { SectionSlug } from "@/lib/sections";

/**
 * A section: the page tree under its root page. A section whose root does
 * not exist yet offers an admin to set it up; anyone else is told to ask one.
 */
export function SectionView({ slug }: { slug: SectionSlug }) {
  const t = useTranslations("sections");
  const tSidebar = useTranslations("sidebar");
  const tPages = useTranslations("pages");
  const tPlaceholder = useTranslations("placeholder");
  const isAdmin = useIsOrganizationAdmin();
  const { section, isPending, isError, error, refetch } = useSectionRoot(slug);
  const setUp = useSetUpSection();
  const title = tSidebar(`sections.${slug}`);

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
  } else if (!section.root && isAdmin) {
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
  } else if (!section.root) {
    body = <EmptyState title={t("notSetUp.title")} description={t("notSetUp.description")} />;
  } else {
    body = <EmptyState title={tPlaceholder("title")} description={tPlaceholder("description")} />;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={title} />
      {body}
    </div>
  );
}
