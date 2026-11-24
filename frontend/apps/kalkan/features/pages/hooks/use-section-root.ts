"use client";

import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { SECTION_ROOT_TITLES, type SectionSlug } from "@/lib/sections";
import { createPage } from "../api";
import { pageErrorText } from "../lib/page-error";
import { childrenOf, findSectionRoot } from "../lib/page-tree";
import { KalkanLocale, type PageListItem } from "../schemas";
import { useInvalidatePages, usePageTree } from "./use-pages";

/** A section as the tree has it: its root page and the root's direct children. */
export type SectionRoot = {
  /** Null when the section is not set up, or the caller holds no level on it. */
  root: PageListItem | null;
  children: PageListItem[];
};

/**
 * The root page of a section, found in the shared page tree by its slug, with
 * its direct children in sibling order. Reads the same query as the sidebar,
 * so it costs no request of its own.
 */
export function useSectionRoot(slug: SectionSlug) {
  const query = usePageTree();
  const pages = query.data?.data;
  const root = pages ? findSectionRoot(pages, slug) : null;
  const section: SectionRoot | undefined = pages
    ? { root, children: root ? childrenOf(pages, root.id) : [] }
    : undefined;
  return { ...query, section };
}

/**
 * Sets a section up: creates its root page, named after the section in both
 * content locales, with an empty body. Only an organization ADMIN or OWNER
 * may create a root page (`KLK_SEC_001` otherwise), so a caller offers this
 * only when `useIsOrganizationAdmin()` is true.
 */
export function useSetUpSection() {
  const invalidate = useInvalidatePages();
  const t = useTranslations("pages");

  return useMutation({
    mutationFn: (slug: SectionSlug) =>
      createPage({
        parentId: null,
        slug,
        compliance: false,
        translations: [
          { locale: KalkanLocale.TR, title: SECTION_ROOT_TITLES[slug].TR, body: "" },
          { locale: KalkanLocale.EN, title: SECTION_ROOT_TITLES[slug].EN, body: "" },
        ],
      }),
    onSuccess: () => {
      toast.success(t("toast.sectionSetUp"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(pageErrorText(error, t, "toast.sectionSetUpError"));
    },
  });
}
