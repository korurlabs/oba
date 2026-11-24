"use client";

import { useCallback, useEffect, useId, useMemo, useState, type ReactNode } from "react";
import { useFormContext, useWatch, type UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import { MarkdownEditor } from "@oba/shared/components/markdown-editor-lazy";
import type { MarkdownEditorComponents } from "@oba/shared/components/markdown-editor-components";
import {
  CheckboxField,
  FormGrid,
  ObaForm,
  SelectField,
  TextField,
  type SelectOption,
} from "@oba/shared/features/form";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { useUploadPageAttachments } from "../hooks/use-page-attachments";
import { PAGE_LIMITS, type KalkanLocale } from "../schemas";
import {
  appendToBody,
  createPageFormSchema,
  PAGE_FORM_LOCALES,
  slugFromTitle,
  type PageFormValues,
} from "../schemas/page-form";
import { PageAttachmentsCard } from "./page-attachments-card";

/** The editor's toolbar and link popover use the Sky button, as the rest of the form does. */
const EDITOR_COMPONENTS: Partial<MarkdownEditorComponents> = { Button };

interface PageFormProps {
  mode: "create" | "edit";
  /** The values the form starts from; a new value resets the form. */
  initialValues: PageFormValues;
  /** The languages the stored page has; their titles cannot be emptied. */
  requiredLocales?: readonly KalkanLocale[];
  /** The pages a new page can go under; shown on create only. */
  parentOptions?: SelectOption[];
  /** The language tab that opens first. */
  initialLocale: KalkanLocale;
  /** The stored page, on edit: its files are listed and new ones attached to it. */
  pageId?: string;
  /** The page is a section root, on edit: its address is the section's route and stays fixed. */
  isRoot?: boolean;
  isPending: boolean;
  onSubmit: (values: PageFormValues, form: UseFormReturn<PageFormValues>) => void;
  /** Shown above the panels, such as the notice that someone else saved the page. */
  notice?: ReactNode;
  /** Extra controls before the submit button on the action bar, such as a cancel link. */
  secondaryAction?: ReactNode;
}

/**
 * The page editor of the create and the edit screen: the title and Markdown
 * body per language on tabs, the address and compliance flag, and on edit
 * the page's files, over a sticky action bar.
 */
export function PageForm({
  mode,
  initialValues,
  requiredLocales,
  parentOptions,
  initialLocale,
  pageId,
  isRoot = false,
  isPending,
  onSubmit,
  notice,
  secondaryAction,
}: PageFormProps) {
  const t = useTranslations("pageForm");
  const schema = useMemo(
    () => createPageFormSchema(t, { requiredLocales, requireParent: mode === "create" }),
    [t, requiredLocales, mode]
  );

  return (
    <ObaForm
      schema={schema}
      onSubmit={onSubmit}
      defaultValues={initialValues}
      className="flex flex-col gap-6"
    >
      {notice}
      <PageFormFields
        mode={mode}
        parentOptions={parentOptions}
        initialLocale={initialLocale}
        pageId={pageId}
        isRoot={isRoot}
        isPending={isPending}
      />
      <PageFormActions mode={mode} isPending={isPending} secondaryAction={secondaryAction} />
    </ObaForm>
  );
}

function PageFormFields({
  mode,
  parentOptions,
  initialLocale,
  pageId,
  isRoot,
  isPending,
}: {
  mode: "create" | "edit";
  parentOptions?: SelectOption[];
  initialLocale: KalkanLocale;
  pageId?: string;
  isRoot: boolean;
  isPending: boolean;
}) {
  const t = useTranslations("pageForm");
  const { control, formState, getValues, setValue } = useFormContext<PageFormValues>();
  const [activeLocale, setActiveLocale] = useState<KalkanLocale>(initialLocale);
  // On create the address follows the title until the user types one.
  const [slugEdited, setSlugEdited] = useState(mode === "edit");
  const upload = useUploadPageAttachments({
    // The editor shows its own message when an upload from its toolbar fails.
    suppressErrorToast: () => true,
  });

  const insertLink = useCallback(
    (link: string) => {
      const field = `translations.${activeLocale}.body` as const;
      setValue(field, appendToBody(getValues(field), link), {
        shouldDirty: true,
        shouldValidate: formState.isSubmitted,
      });
    },
    [activeLocale, getValues, setValue, formState.isSubmitted]
  );

  const uploadFromEditor = useMemo(() => {
    if (!pageId) return undefined;
    return async (file: File) => {
      const response = await upload.mutateAsync({ pageId, files: [file] });
      const stored = response.data?.[0];
      if (!stored) {
        throw new Error(t("attachments.uploadMissing"));
      }
      return {
        url: stored.url,
        isImage: file.type.startsWith("image/"),
        filename: stored.fileName,
      };
    };
  }, [pageId, upload, t]);

  return (
    <>
      {mode === "create" && !slugEdited && <SlugFollowsTitle preferredLocale={initialLocale} />}

      <FormSection title={t("sections.content")}>
        <Tabs
          value={activeLocale}
          onValueChange={(value) => setActiveLocale(value as KalkanLocale)}
        >
          <TabsList aria-label={t("languages")}>
            {PAGE_FORM_LOCALES.map((locale) => (
              <TabsTrigger key={locale} value={locale}>
                {t(`locales.${locale}`)}
                {formState.errors.translations?.[locale] ? (
                  <span className="bg-destructive size-1.5 rounded-full" aria-hidden="true" />
                ) : null}
                {formState.errors.translations?.[locale] ? (
                  <span className="sr-only">{t("tabHasErrors")}</span>
                ) : null}
              </TabsTrigger>
            ))}
          </TabsList>
          {PAGE_FORM_LOCALES.map((locale) => (
            <TabsContent
              key={locale}
              value={locale}
              // Both editors stay mounted, so switching tabs keeps each editor's state.
              forceMount
              className="data-[state=inactive]:hidden"
            >
              <div className="flex flex-col gap-4">
                <TextField
                  name={`translations.${locale}.title`}
                  label={t("title")}
                  control={control}
                  maxLength={PAGE_LIMITS.titleMax}
                  disabled={isPending}
                />
                <MarkdownEditor
                  name={`translations.${locale}.body`}
                  control={control}
                  label={t("body")}
                  placeholder={t("bodyPlaceholder")}
                  components={EDITOR_COMPONENTS}
                  className="min-h-[24rem]"
                  onFileUpload={uploadFromEditor}
                  disabled={isPending}
                />
              </div>
            </TabsContent>
          ))}
        </Tabs>
      </FormSection>

      <FormSection title={t("sections.settings")}>
        <FormGrid className="md:grid-cols-6">
          {mode === "create" && (
            <div className="md:col-span-6">
              <SelectField
                name="parentId"
                label={t("parent")}
                placeholder={t("parentPlaceholder")}
                searchPlaceholder={t("parentSearch")}
                control={control}
                options={parentOptions ?? []}
                renderOption={renderParentOption}
                renderValue={renderParentValue}
                required
                disabled={isPending}
              />
            </div>
          )}
          <div className="md:col-span-3">
            <TextField
              name="slug"
              label={t("slug")}
              description={isRoot ? t("slugRootHint") : t("slugHint")}
              control={control}
              required
              maxLength={100}
              readOnly={isRoot}
              disabled={isPending}
              onInput={() => setSlugEdited(true)}
            />
          </div>
          <div className="md:col-span-3 md:pt-7">
            <CheckboxField
              name="compliance"
              label={t("compliance")}
              description={t("complianceHint")}
              control={control}
              disabled={isPending}
            />
          </div>
        </FormGrid>
      </FormSection>

      {pageId ? (
        <PageAttachmentsCard
          pageId={pageId}
          upload={{ insertTargetLabel: t(`locales.${activeLocale}`), onInsertLink: insertLink }}
        />
      ) : (
        <p className="text-muted-foreground text-sm">{t("attachments.afterCreate")}</p>
      )}
    </>
  );
}

/**
 * Keeps the address in step with the title while the user has not typed one:
 * the title in the reader's language, else the other one.
 */
function SlugFollowsTitle({ preferredLocale }: { preferredLocale: KalkanLocale }) {
  const { control, setValue, getValues, formState } = useFormContext<PageFormValues>();
  const translations = useWatch({ control, name: "translations" });
  const other = PAGE_FORM_LOCALES.find((locale) => locale !== preferredLocale) ?? preferredLocale;
  const title =
    translations[preferredLocale].title.trim() !== ""
      ? translations[preferredLocale].title
      : translations[other].title;
  const suggested = slugFromTitle(title);
  const { isSubmitted } = formState;

  useEffect(() => {
    if (getValues("slug") !== suggested) {
      setValue("slug", suggested, { shouldValidate: isSubmitted });
    }
  }, [suggested, getValues, setValue, isSubmitted]);
  return null;
}

/** A parent option, indented by its depth under the section. */
function renderParentOption(option: SelectOption): ReactNode {
  const depth = typeof option.meta?.depth === "number" ? option.meta.depth : 0;
  return (
    <span className="truncate" style={{ paddingLeft: `${depth * 0.75}rem` }}>
      {option.label}
    </span>
  );
}

/** The chosen parent in the trigger: its name, without the tree indent. */
function renderParentValue(option: SelectOption): ReactNode {
  return <span className="truncate">{option.label}</span>;
}

/** One group of fields: a white rounded panel headed by its title. */
function FormSection({ title, children }: { title: string; children: ReactNode }) {
  const headingId = useId();

  return (
    <Card role="region" aria-labelledby={headingId}>
      <CardHeader>
        <h2 id={headingId} className="text-lg leading-none">
          {title}
        </h2>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

/**
 * The form's actions on a bar that sticks to the bottom of the page while the
 * form scrolls. On edit it also says when there are unsaved changes and can
 * put the loaded values back.
 */
function PageFormActions({
  mode,
  isPending,
  secondaryAction,
}: {
  mode: "create" | "edit";
  isPending: boolean;
  secondaryAction?: ReactNode;
}) {
  const t = useTranslations("pageForm");
  const {
    formState: { isDirty },
    reset,
  } = useFormContext<PageFormValues>();
  const showDirty = mode === "edit" && isDirty;

  return (
    <div className="bg-card/95 sticky bottom-4 z-10 flex flex-wrap items-center justify-end gap-2 rounded-xl border px-4 py-3 shadow-md backdrop-blur">
      <p className="text-warning-text mr-auto text-sm font-medium" role="status">
        {showDirty ? t("unsavedChanges") : null}
      </p>
      {secondaryAction}
      {mode === "edit" && (
        <Button
          type="button"
          variant="outline"
          disabled={!isDirty || isPending}
          onClick={() => reset()}
        >
          {t("discard")}
        </Button>
      )}
      <Button type="submit" disabled={isPending}>
        {mode === "create"
          ? isPending
            ? t("creating")
            : t("create")
          : isPending
            ? t("saving")
            : t("save")}
      </Button>
    </div>
  );
}
