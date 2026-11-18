import { useTranslations } from "next-intl";

/**
 * The sidebar of every Kalkan page, beside the page content: it lists the
 * root pages the user may see, each with its page tree. Below the medium
 * breakpoint it sits above the content instead of beside it.
 */
export function KalkanSidebar() {
  const t = useTranslations("sidebar");

  return (
    <aside
      aria-label={t("label")}
      className="bg-card border-border flex w-full shrink-0 flex-col border-b md:w-64 md:border-r md:border-b-0"
    >
      <div className="min-h-0 flex-1 overflow-y-auto p-2" />
    </aside>
  );
}
