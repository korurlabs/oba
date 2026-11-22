"use client";

import { useLocale, useTranslations } from "next-intl";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { PageHeading } from "@/components/page-states";
import { usePage } from "../hooks/use-pages";
import { pageErrorText } from "../lib/page-error";
import { contentLocaleOf } from "../lib/page-tree";

/** One page: its title, its Markdown body and its actions. */
export function PageView({ pageId }: { pageId: string }) {
  const t = useTranslations("pages");
  const tPlaceholder = useTranslations("placeholder");
  const locale = useLocale();
  const { data, isPending, isError, error, refetch } = usePage(pageId);

  if (isPending) {
    return <TableLoading label={t("view.loading")} />;
  }
  if (isError || !data.data) {
    return (
      <LoadError
        title={t("view.loadError")}
        message={pageErrorText(error, t, "view.loadError")}
        onRetry={() => void refetch()}
        retryLabel={t("view.retry")}
      />
    );
  }

  const page = data.data;
  const preferred = contentLocaleOf(locale);
  const translation =
    page.translations.find((candidate) => candidate.locale === preferred) ?? page.translations[0];

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={translation?.title ?? page.slug} description={page.path} />
      <EmptyState title={tPlaceholder("title")} description={tPlaceholder("description")} />
    </div>
  );
}
