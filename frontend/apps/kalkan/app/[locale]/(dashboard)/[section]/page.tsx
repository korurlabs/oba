import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { SectionView } from "@/features/sections/components/section-view";
import { isSectionSlug } from "@/lib/sections";

type SectionPageProps = { params: Promise<{ section: string }> };

export async function generateMetadata({ params }: SectionPageProps): Promise<Metadata> {
  const { section } = await params;
  if (!isSectionSlug(section)) return {};
  const t = await getTranslations("sidebar");
  return { title: t(`sections.${section}`) };
}

/**
 * Section Page
 *
 * One of the fixed sections of the menu, such as `/policies`; any other
 * segment answers 404. Its tree comes from the page tree the dashboard
 * layout prefetches for the menu, so the page prefetches nothing itself.
 */
export default async function SectionPage({ params }: SectionPageProps) {
  const { section } = await params;
  if (!isSectionSlug(section)) notFound();
  return <SectionView slug={section} />;
}
