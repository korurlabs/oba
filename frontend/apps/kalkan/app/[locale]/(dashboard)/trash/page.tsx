import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { TrashView } from "@/features/trash/components/trash-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("sidebar");
  return { title: t("trash") };
}

/** Trash: the deleted pages a member can restore. The dashboard layout gates auth. */
export default function TrashPage() {
  return <TrashView />;
}
