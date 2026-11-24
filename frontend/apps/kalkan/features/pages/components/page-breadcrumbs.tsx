"use client";

import { Fragment } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@oba/shared/libs/i18n/navigation";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@oba/ui/components/breadcrumb";
import { ancestorsOf } from "@/features/sections/lib/tree-moves";
import { pageHref, sectionHref } from "@/lib/routes";
import { isSectionSlug } from "@/lib/sections";
import { pageTitle } from "../lib/page-tree";
import type { PageDetail, PageListItem } from "../schemas";

/** One step of the trail: its label and where it leads. */
type Crumb = { id: string; label: string; href: string };

/**
 * The way from the page's section down to the page, read from the shared
 * page tree. A section root is named and linked as its section, so the trail
 * starts where the sidebar does; any other page links to its own view. Until
 * the tree has loaded the trail holds the page alone.
 */
export function PageBreadcrumbs({
  page,
  title,
  pages,
}: {
  page: Pick<PageDetail, "id" | "parentId" | "slug">;
  title: string;
  pages: readonly PageListItem[];
}) {
  const t = useTranslations("pageView");
  const tSidebar = useTranslations("sidebar");
  const locale = useLocale();

  const crumbOf = (candidate: Pick<PageListItem, "id" | "parentId" | "slug" | "titles">): Crumb => {
    const slug = candidate.slug.toLowerCase();
    if (candidate.parentId === null && isSectionSlug(slug)) {
      return { id: candidate.id, label: tSidebar(`sections.${slug}`), href: sectionHref(slug) };
    }
    return { id: candidate.id, label: pageTitle(candidate, locale), href: pageHref(candidate.id) };
  };

  const crumbs = ancestorsOf(pages, page.id).map(crumbOf);
  // A section's root page sits below its section in the trail.
  if (page.parentId === null) {
    const self = pages.find((candidate) => candidate.id === page.id);
    const sectionCrumb = self ? crumbOf(self) : null;
    if (sectionCrumb && sectionCrumb.href !== pageHref(page.id)) crumbs.push(sectionCrumb);
  }

  return (
    <Breadcrumb aria-label={t("breadcrumb")}>
      <BreadcrumbList>
        {crumbs.map((crumb) => (
          <Fragment key={crumb.id + crumb.href}>
            <BreadcrumbItem>
              <BreadcrumbLink asChild>
                <Link href={crumb.href}>{crumb.label}</Link>
              </BreadcrumbLink>
            </BreadcrumbItem>
            <BreadcrumbSeparator />
          </Fragment>
        ))}
        <BreadcrumbItem>
          <BreadcrumbPage>{title}</BreadcrumbPage>
        </BreadcrumbItem>
      </BreadcrumbList>
    </Breadcrumb>
  );
}
