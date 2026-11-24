"use client";

import { useState, type FormEvent, type KeyboardEvent } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { OPS_GRID_HIGHLIGHT, OPS_GRID_SURFACE } from "@oba/ui/components/ops-grid";
import { Input } from "@oba/ui/sky/field";
import { pageEditHref } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { useCreatePage } from "../hooks/use-pages";
import { contentLocaleOf } from "../lib/page-tree";
import type { PageCreateRequest } from "../schemas";
import { createChildPageNameSchema, uniqueChildSlug } from "../schemas/page-form";

export type NewChildPageFieldProps = {
  /** The page the new page goes under. */
  parentId: string;
  /** The slugs the parent's children already hold, so the new slug avoids them. */
  siblingSlugs: readonly string[];
  /** Closes the field without creating a page: Escape, or Enter on an empty name. */
  onCancel: () => void;
  /** Closes the field once the page exists. */
  onCreated: () => void;
};

/**
 * The name field the menu's tree opens under a row to add a page there. Enter
 * creates the page with that title in the reader's content locale, an empty
 * body and a slug made from the title, then opens its editor. A refused
 * create keeps the field and the name, so the user can correct it; the
 * create hook shows why in a toast.
 */
export function NewChildPageField({
  parentId,
  siblingSlugs,
  onCancel,
  onCreated,
}: NewChildPageFieldProps) {
  const t = useTranslations("sidebar");
  const locale = useLocale();
  const router = useRouter();
  const createPage = useCreatePage();
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (createPage.isPending) return;
    const title = name.trim();
    if (title === "") {
      onCancel();
      return;
    }
    const parsed = createChildPageNameSchema(t).safeParse(title);
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? t("newChildNameMax"));
      return;
    }
    const request: PageCreateRequest = {
      parentId,
      slug: uniqueChildSlug(parsed.data, siblingSlugs),
      position: null,
      compliance: false,
      translations: [{ locale: contentLocaleOf(locale), title: parsed.data, body: "" }],
    };
    createPage.mutate(request, {
      onSuccess: (response) => {
        onCreated();
        if (response.data) {
          router.push(pageEditHref(response.data.id));
        }
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
        disabled={createPage.isPending}
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
