"use client";

import { useMemo } from "react";
import { useFormContext, useWatch, type Path, type UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import { CheckCircle2, Copy } from "lucide-react";
import { toast } from "sonner";
import {
  FormGrid,
  ObaForm,
  SelectField,
  TextareaField,
  TextField,
} from "@oba/shared/features/form";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { DocumentLanguageSchema } from "@/features/document-templates/schemas";
import { useLegalEntities } from "@/features/documents/hooks/use-legal-entities";
import type { LegalEntityListItem } from "@/features/documents/schemas";
import { cn } from "@/lib/utils";
import { useCreateCampaignContent, useUpdateCampaignContent } from "../hooks/use-campaigns";
import { campaignErrorText, campaignFieldErrors } from "../lib/campaign-error";
import {
  CampaignChannelSchema,
  CampaignStatusSchema,
  campaignContentToFormValues,
  canMarkContentPublished,
  CHANNEL_TEXT_LIMITS,
  createCampaignContentFormSchema,
  emptyCampaignContentFormValues,
  toCampaignContentRequest,
  toMarkPublishedRequest,
  type CampaignContent,
  type CampaignContentFormValues,
} from "../schemas";
import { CampaignContentImagesCard } from "./campaign-content-images-card";

interface CampaignContentFormDialogProps {
  campaignId: string;
  /** The content to edit, or null for a new one. */
  content: CampaignContent | null;
  /** Called with the stored content after a create or "mark as published". */
  onSaved: (content: CampaignContent) => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Creates or edits one content of a campaign: its legal entity, channel,
 * language, status, moments, post address and text, and its images. A new
 * content stays open after it is created, so its images can be added; an
 * edit closes once the server has stored it.
 */
export function CampaignContentFormDialog({
  campaignId,
  content,
  onSaved,
  open,
  onOpenChange,
}: CampaignContentFormDialogProps) {
  const t = useTranslations("campaigns");
  const tErrors = useTranslations("errors");
  const tForm = useTranslations("campaigns.contentForm");
  const schema = useMemo(() => createCampaignContentFormSchema(tForm), [tForm]);
  const legalEntities = useLegalEntities();
  const entities = legalEntities.data?.data;
  const createContent = useCreateCampaignContent();
  const updateContent = useUpdateCampaignContent();
  const markPublished = useUpdateCampaignContent({ successKey: "toast.contentMarkedPublished" });
  const isPending = createContent.isPending || updateContent.isPending || markPublished.isPending;

  const handleSubmit = (
    values: CampaignContentFormValues,
    form: UseFormReturn<CampaignContentFormValues>
  ) => {
    // The schema refuses an empty channel or language, so a submitted form has both.
    if (values.channel === "" || values.language === "") return;
    const data = toCampaignContentRequest(values, values.channel, values.language);
    const onError = (error: unknown) => applyContentFieldErrors(form, error, t);
    if (content === null) {
      createContent.mutate(
        { campaignId, data },
        {
          onSuccess: (response) => {
            if (response.data) onSaved(response.data);
          },
          onError,
        }
      );
    } else {
      updateContent.mutate(
        { campaignId, contentId: content.id, data },
        { onSuccess: () => onOpenChange(false), onError }
      );
    }
  };

  const handleMarkPublished = () => {
    if (content === null) return;
    markPublished.mutate(
      { campaignId, contentId: content.id, data: toMarkPublishedRequest(content) },
      {
        onSuccess: (response) => {
          if (response.data) onSaved(response.data);
        },
      }
    );
  };

  let defaultValues: CampaignContentFormValues;
  if (content !== null) {
    defaultValues = campaignContentToFormValues(content);
  } else {
    // An organization with one legal entity has nothing to pick.
    const onlyEntityId = entities?.length === 1 ? (entities[0]?.id ?? "") : "";
    defaultValues = emptyCampaignContentFormValues(onlyEntityId);
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{content ? t("contentEditTitle") : t("contentNewTitle")}</DialogTitle>
          <DialogDescription>{t("contentDialogDescription")}</DialogDescription>
        </DialogHeader>
        {legalEntities.isError && entities === undefined ? (
          <LoadError
            title={t("legalEntitiesLoadError")}
            message={campaignErrorText(legalEntities.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void legalEntities.refetch()}
          />
        ) : entities === undefined ? (
          <TableLoading label={t("legalEntitiesLoading")} />
        ) : entities.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t("noLegalEntities")}</p>
        ) : (
          // Mounted per open and per stored version, so the form starts from
          // the content as the server has it, not from the last input.
          open && (
            <div className="flex flex-col gap-6">
              <ObaForm
                key={content ? `${content.id}:${content.updatedAt}` : "new"}
                schema={schema}
                defaultValues={defaultValues}
                onSubmit={handleSubmit}
                className="space-y-6"
              >
                <CampaignContentFormFields legalEntities={entities} isPending={isPending} />
                <DialogFooter className="sm:justify-between">
                  <div>
                    {content !== null && canMarkContentPublished(content) && (
                      <Button
                        type="button"
                        variant="outline"
                        disabled={isPending}
                        onClick={handleMarkPublished}
                      >
                        <CheckCircle2 aria-hidden="true" />
                        {markPublished.isPending
                          ? tForm("markingPublished")
                          : tForm("markPublished")}
                      </Button>
                    )}
                  </div>
                  <div className="flex flex-col-reverse gap-2 sm:flex-row">
                    <Button
                      type="button"
                      variant="outline"
                      disabled={isPending}
                      onClick={() => onOpenChange(false)}
                    >
                      {content ? tForm("close") : tForm("cancel")}
                    </Button>
                    <Button type="submit" disabled={isPending}>
                      {isPending ? tForm("saving") : content ? tForm("save") : tForm("create")}
                    </Button>
                  </div>
                </DialogFooter>
              </ObaForm>
              {content !== null ? (
                <CampaignContentImagesCard contentId={content.id} />
              ) : (
                <p className="text-muted-foreground text-sm">{tForm("imagesAfterCreate")}</p>
              )}
            </div>
          )
        )}
      </DialogContent>
    </Dialog>
  );
}

/** Puts the fields a refused save names on the form. */
function applyContentFieldErrors(
  form: UseFormReturn<CampaignContentFormValues>,
  error: unknown,
  t: (key: string) => string
): void {
  const fields = Object.keys(form.getValues());
  for (const [field, message] of Object.entries(campaignFieldErrors(error, t))) {
    if (fields.includes(field)) {
      form.setError(field as Path<CampaignContentFormValues>, { type: "server", message });
    }
  }
}

function CampaignContentFormFields({
  legalEntities,
  isPending,
}: {
  legalEntities: LegalEntityListItem[];
  isPending: boolean;
}) {
  const t = useTranslations("campaigns");
  const tTemplates = useTranslations("documentTemplates");
  const { control } = useFormContext<CampaignContentFormValues>();

  const legalEntityOptions = legalEntities.map((entity) => ({
    value: entity.id,
    label: entity.shortName,
  }));
  const channelOptions = CampaignChannelSchema.options.map((channel) => ({
    value: channel,
    label: t(`channels.${channel}`),
  }));
  const languageOptions = DocumentLanguageSchema.options.map((language) => ({
    value: language,
    label: tTemplates(`languages.${language}`),
  }));
  const statusOptions = CampaignStatusSchema.options.map((status) => ({
    value: status,
    label: t(`statuses.${status}`),
  }));

  return (
    <div className="flex flex-col gap-6">
      <FormGrid>
        <SelectField
          name="legalEntityId"
          label={t("contentForm.legalEntity")}
          placeholder={t("contentForm.legalEntityPlaceholder")}
          control={control}
          options={legalEntityOptions}
          required
          disabled={isPending}
        />
        <SelectField
          name="channel"
          label={t("contentForm.channel")}
          placeholder={t("contentForm.channelPlaceholder")}
          control={control}
          options={channelOptions}
          required
          disabled={isPending}
        />
        <SelectField
          name="language"
          label={t("contentForm.language")}
          placeholder={t("contentForm.languagePlaceholder")}
          control={control}
          options={languageOptions}
          required
          disabled={isPending}
        />
        <SelectField
          name="status"
          label={t("contentForm.status")}
          control={control}
          options={statusOptions}
          required
          disabled={isPending}
        />
        {/* A datetime-local input gives the viewer's wall-clock time; it is sent as a UTC instant. */}
        <TextField
          name="plannedAt"
          type="datetime-local"
          label={t("contentForm.plannedAt")}
          description={t("contentForm.plannedAtHint")}
          control={control}
          disabled={isPending}
        />
        <TextField
          name="publishedAt"
          type="datetime-local"
          label={t("contentForm.publishedAt")}
          description={t("contentForm.publishedAtHint")}
          control={control}
          disabled={isPending}
        />
        <div className="md:col-span-2">
          <TextField
            name="postUrl"
            label={t("contentForm.postUrl")}
            placeholder={t("contentForm.postUrlPlaceholder")}
            control={control}
            disabled={isPending}
          />
        </div>
      </FormGrid>
      <CampaignContentTextField isPending={isPending} />
    </div>
  );
}

/**
 * The text of the post with its length against the channel's post limit and
 * a copy button, so the member pastes the exact text into the post. The
 * limit is a hint: the server stores longer texts.
 */
function CampaignContentTextField({ isPending }: { isPending: boolean }) {
  const t = useTranslations("campaigns");
  const { control } = useFormContext<CampaignContentFormValues>();
  const [channel, text] = useWatch({ control, name: ["channel", "text"] });
  const limit = channel === "" ? undefined : CHANNEL_TEXT_LIMITS[channel];

  const copy = () => {
    navigator.clipboard.writeText(text).then(
      () => toast.success(t("contentForm.copied")),
      (error: unknown) => {
        console.error("[campaign-content] copy to the clipboard failed:", error);
        toast.error(t("contentForm.copyError"));
      }
    );
  };

  let countText: string;
  if (limit === undefined) {
    countText = t("contentForm.characterCount", { count: text.length });
  } else {
    countText = t("contentForm.characterCountOfLimit", { count: text.length, limit });
  }

  return (
    <div className="flex flex-col gap-1">
      <TextareaField
        name="text"
        label={t("contentForm.text")}
        placeholder={t("contentForm.textPlaceholder")}
        control={control}
        rows={10}
        required
        disabled={isPending}
      />
      <div className="flex items-center justify-end gap-2">
        <p
          className={cn(
            "text-muted-foreground text-xs tabular-nums",
            limit !== undefined && text.length > limit && "text-warning-text"
          )}
          aria-live="polite"
        >
          {countText}
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={text.trim() === ""}
          onClick={copy}
        >
          <Copy aria-hidden="true" />
          {t("contentForm.copy")}
        </Button>
      </div>
    </div>
  );
}
