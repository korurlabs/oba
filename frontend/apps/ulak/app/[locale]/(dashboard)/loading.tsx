import { useTranslations } from "next-intl";
import { TableLoading } from "@oba/ui/sky/page-states";

/**
 * Dashboard Loading State
 * Next.js automatically shows this while page.tsx is loading. The dashboard's
 * pages are an organization's, so it names that, in the outline Kaya's pages
 * show while they load.
 */
export default function UlakLoading() {
  const t = useTranslations("organization");

  return <TableLoading label={t("loadingOrganization")} />;
}
