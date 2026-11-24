"use client";

import { useTranslations } from "next-intl";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Card, CardDescription, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { sectionHref } from "@/lib/routes";
import { SECTION_GROUPS, type SectionSlug } from "@/lib/sections";
import type { SectionCount } from "../lib/section-counts";

/**
 * A card per section, grouped as the sidebar groups them, each with its page
 * count and a link to the section. A section without a root the user can see
 * says it is not set up; the section page itself offers the set-up.
 */
export function SectionCards({ counts }: { counts: Record<SectionSlug, SectionCount> }) {
  const t = useTranslations("overview");
  const tSidebar = useTranslations("sidebar");

  return (
    <div className="flex flex-col gap-6">
      {SECTION_GROUPS.map((group) => (
        <section
          key={group.key}
          aria-labelledby={`overview-group-${group.key}`}
          className="flex flex-col gap-3"
        >
          <h2
            id={`overview-group-${group.key}`}
            className="text-muted-foreground text-xs font-semibold tracking-wide uppercase"
          >
            {tSidebar(`groups.${group.key}`)}
          </h2>
          <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {group.slugs.map((slug) => {
              const count = counts[slug];
              return (
                <li key={slug}>
                  <Link
                    href={sectionHref(slug)}
                    className="focus-visible:ring-ring block rounded-xl focus-visible:ring-2 focus-visible:outline-none"
                  >
                    <Card className="gap-2 py-4 transition-shadow hover:shadow-md">
                      <CardHeader className="px-4">
                        <CardTitle>{tSidebar(`sections.${slug}`)}</CardTitle>
                        <CardDescription className="tabular-nums">
                          {count === null
                            ? t("notSetUp")
                            : t("pageCount", { count: count.pageCount })}
                        </CardDescription>
                      </CardHeader>
                    </Card>
                  </Link>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}
