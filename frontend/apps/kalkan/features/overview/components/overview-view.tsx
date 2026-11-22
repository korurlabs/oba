import { useTranslations } from "next-intl";
import { PagePlaceholder } from "@/components/layout/page-placeholder";

/** The start page: the state of every section and the latest changes. */
export function OverviewView() {
  const t = useTranslations("sidebar");
  return <PagePlaceholder title={t("overview")} />;
}
