import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { RecentView } from "@/features/recent/components/recent-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("sidebar");
  return { title: t("recent") };
}

/** Recent: the pages changed most recently. The dashboard layout gates auth. */
export default function RecentPage() {
  return <RecentView />;
}
