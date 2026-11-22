import { useTranslations } from "next-intl";
import { PagePlaceholder } from "@/components/layout/page-placeholder";

/** The pages changed most recently, across every section the user can see. */
export function RecentView() {
  const t = useTranslations("sidebar");
  return <PagePlaceholder title={t("recent")} />;
}
