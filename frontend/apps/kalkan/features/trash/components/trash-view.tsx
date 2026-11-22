import { useTranslations } from "next-intl";
import { PagePlaceholder } from "@/components/layout/page-placeholder";

/** The deleted pages the user can restore. */
export function TrashView() {
  const t = useTranslations("sidebar");
  return <PagePlaceholder title={t("trash")} />;
}
