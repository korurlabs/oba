"use client";

import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { FilterLabel } from "@/components/page-states";
import {
  useCopyDocumentTemplateToAttachment,
  useDocumentTemplates,
} from "../hooks/use-document-templates";
import { documentTemplateErrorText } from "../lib/document-template-error";
import type {
  DocumentTemplateAttachmentEntityType,
  DocumentTemplateQueryRequest,
} from "../schemas";

/** The most templates the picker lists at once; the search narrows a longer library. */
const TEMPLATE_PICKER_SIZE = 50;

export interface AttachmentFromTemplateDialogProps {
  entityType: DocumentTemplateAttachmentEntityType;
  entityId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Adds a document template's PDF to a mail or a relation as an attachment:
 * the user searches the organization's templates by name and picks one, and
 * the server stores a copy, so a later change to the template leaves the
 * attachment as it is. A template that still needs its files is shown but
 * can not be picked.
 */
export function AttachmentFromTemplateDialog({
  entityType,
  entityId,
  open,
  onOpenChange,
}: AttachmentFromTemplateDialogProps) {
  const t = useTranslations("documentTemplates");
  const tErrors = useTranslations("errors");
  const [search, setSearch] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const copyToAttachment = useCopyDocumentTemplateToAttachment();
  useEffect(() => {
    if (!open) {
      setSearch(null);
      setSelectedId(null);
    }
  }, [open]);

  const query = useMemo(
    (): DocumentTemplateQueryRequest => ({
      search: search ?? undefined,
      page: 1,
      amount: TEMPLATE_PICKER_SIZE,
    }),
    [search]
  );
  const templates = useDocumentTemplates(query, { enabled: open });
  const rows = templates.data?.data ?? [];
  const isPending = copyToAttachment.isPending;

  const handleAdd = () => {
    if (selectedId === null) return;
    copyToAttachment.mutate(
      { templateId: selectedId, data: { entityType, entityId } },
      { onSuccess: () => onOpenChange(false) }
    );
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("attach.title")}</DialogTitle>
          <DialogDescription>{t("attach.description")}</DialogDescription>
        </DialogHeader>
        <FilterLabel label={t("filters.searchLabel")} className="w-full">
          <SenseySearchInput
            value={search}
            placeholder={t("filters.searchPlaceholder")}
            onSearchChange={(next) => {
              setSearch(next ?? null);
              setSelectedId(null);
            }}
            className="w-full"
          />
        </FilterLabel>
        {templates.isError && templates.data === undefined ? (
          <LoadError
            title={t("loadError")}
            message={documentTemplateErrorText(templates.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void templates.refetch()}
          />
        ) : templates.data === undefined ? (
          <TableLoading label={t("loading")} />
        ) : rows.length === 0 ? (
          <p className="text-muted-foreground text-sm" role="status">
            {search ? t("emptyFiltered") : t("attach.empty")}
          </p>
        ) : (
          <fieldset className="flex max-h-[50vh] flex-col gap-1 overflow-y-auto">
            <legend className="sr-only">{t("attach.pick")}</legend>
            {rows.map((template) => (
              <label
                key={template.id}
                className={
                  template.ready
                    ? "hover:bg-muted has-[:checked]:border-primary flex cursor-pointer items-center gap-3 rounded-md border px-3 py-2"
                    : "flex cursor-not-allowed items-center gap-3 rounded-md border px-3 py-2 opacity-60"
                }
              >
                <input
                  type="radio"
                  name="template"
                  value={template.id}
                  checked={selectedId === template.id}
                  disabled={!template.ready || isPending}
                  onChange={() => setSelectedId(template.id)}
                />
                <span className="min-w-0 flex-1 truncate text-sm font-medium">{template.name}</span>
                <span className="text-muted-foreground text-sm whitespace-nowrap">
                  {`${t(`types.${template.type}`)} · ${t(`languages.${template.language}`)}`}
                </span>
                {!template.ready && <Badge variant="warning">{t("ready.no")}</Badge>}
              </label>
            ))}
          </fieldset>
        )}
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            disabled={isPending}
            onClick={() => onOpenChange(false)}
          >
            {t("attach.cancel")}
          </Button>
          <Button type="button" disabled={isPending || selectedId === null} onClick={handleAdd}>
            {isPending ? t("attach.adding") : t("attach.add")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
