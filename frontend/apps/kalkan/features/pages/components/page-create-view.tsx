import { useTranslations } from "next-intl";
import { PagePlaceholder } from "@/components/layout/page-placeholder";

/** The form that creates a page under a section or another page. */
export function PageCreateView() {
  const t = useTranslations("sidebar");
  return <PagePlaceholder title={t("newPage")} />;
}
