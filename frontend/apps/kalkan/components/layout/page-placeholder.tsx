import { useTranslations } from "next-intl";
import { EmptyState } from "@oba/ui/sky/page-states";
import { PageHeading } from "@/components/page-states";

/**
 * The heading of a page whose content is still to come, and a note that it
 * is on its way, so every route of the menu already opens a real page.
 */
export function PagePlaceholder({ title }: { title: string }) {
  const t = useTranslations("placeholder");
  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={title} />
      <EmptyState title={t("title")} description={t("description")} />
    </div>
  );
}
