"use client";

import { useState, type FormEvent, type KeyboardEvent } from "react";
import { useLocale, useTranslations } from "next-intl";
import { toast } from "sonner";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { OPS_GRID_HIGHLIGHT, OPS_GRID_SURFACE } from "@oba/ui/components/ops-grid";
import { Input } from "@oba/ui/sky/field";
import { pageEditHref } from "@/lib/routes";
import type { SectionSlug } from "@/lib/sections";
import { cn } from "@/lib/utils";
import { useCreatePage } from "../hooks/use-pages";
import { useSetUpSection } from "../hooks/use-section-root";
import { contentLocaleOf } from "../lib/page-tree";
import type { PageCreateRequest } from "../schemas";
import { createChildPageNameSchema, uniqueChildSlug } from "../schemas/page-form";

/**
 * Where the new page goes: under an existing page, or under the root page of
 * a section that has none yet, which the field then creates first.
 */
type NewChildPageParent =
  | { parentId: string; setUpSection?: never }
  | { parentId: null; setUpSection: SectionSlug };

export type NewChildPageFieldProps = NewChildPageParent & {
  /** The slugs the parent's children already hold, so the new slug avoids them. */
  siblingSlugs: readonly string[];
  /** Closes the field without creating a page: Escape, or Enter on an empty name. */
  onCancel: () => void;
  /** Closes the field once the page exists, with the id of the page it went under. */
  onCreated: (parentId: string) => void;
};

/**
 * The name field the menu's tree opens under a row to add a page there. Enter
 * creates the page with that title in the reader's content locale, an empty
 * body and a slug made from the title, then opens its editor. A refused
 * create keeps the field and the name, so the user can correct it; the
 * create hook shows why in a toast.
 *
 * In a section that is not set up yet the field first sets the section up,
 * as the section page does, and then adds the page under the new root. A
 * refused set-up leaves nothing behind. A refused page after a set-up keeps
 * the section set up, and the next Enter adds only the page.
 */
export function NewChildPageField({
  parentId,
  setUpSection,
  siblingSlugs,
  onCancel,
  onCreated,
}: NewChildPageFieldProps) {
  const t = useTranslations("sidebar");
  const locale = useLocale();
  const router = useRouter();
  const tPages = useTranslations("pages");
  const createPage = useCreatePage();
  const setUp = useSetUpSection();
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  // The root this field created, until the refetched tree hands it in as
  // `parentId`: a second Enter in between must not set the section up again.
  const [createdRootId, setCreatedRootId] = useState<string | null>(null);
  const isPending = createPage.isPending || setUp.isPending;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isPending) return;
    const typed = name.trim();
    if (typed === "") {
      onCancel();
      return;
    }
    const parsed = createChildPageNameSchema(t).safeParse(typed);
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? t("newChildNameMax"));
      return;
    }
    const title = parsed.data;

    const createChild = (parent: string, slugsTaken: readonly string[]) => {
      const request: PageCreateRequest = {
        parentId: parent,
        slug: uniqueChildSlug(title, slugsTaken),
        position: null,
        compliance: false,
        translations: [{ locale: contentLocaleOf(locale), title, body: "" }],
      };
      createPage.mutate(request, {
        onSuccess: (response) => {
          onCreated(parent);
          if (response.data) {
            router.push(pageEditHref(response.data.id));
          }
        },
      });
    };

    if (parentId !== null) {
      createChild(parentId, siblingSlugs);
      return;
    }
    if (createdRootId !== null) {
      createChild(createdRootId, []);
      return;
    }
    setUp.mutate(setUpSection, {
      onSuccess: (response) => {
        const rootId = response.data?.id;
        if (!rootId) {
          // The section is set up, but without the root's id the page cannot
          // go under it; the refetched tree brings the root for a retry.
          toast.error(tPages("toast.createError"));
          return;
        }
        setCreatedRootId(rootId);
        // A new root has no children, so no slug is taken yet.
        createChild(rootId, []);
      },
    });
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      onCancel();
    }
  };

  return (
    <form onSubmit={handleSubmit} className="px-1 py-1 group-data-[collapsible=icon]:hidden">
      <Input
        // The field opens on a click of "+", so it takes the focus at once.
        autoFocus
        value={name}
        onChange={(event) => {
          setName(event.target.value);
          setError(null);
        }}
        onKeyDown={handleKeyDown}
        disabled={isPending}
        placeholder={t("newChildName")}
        aria-label={t("newChildName")}
        aria-invalid={error !== null}
        className={cn(OPS_GRID_SURFACE, OPS_GRID_HIGHLIGHT, "h-7 text-[13px]")}
      />
      {error !== null && (
        <p role="alert" className="text-destructive mt-1 px-1 text-xs">
          {error}
        </p>
      )}
    </form>
  );
}
