"use client";

import { useMemo } from "react";
import { useFormContext } from "react-hook-form";
import { useTranslations } from "next-intl";
import { SelectField, TextField, type SelectOption } from "@oba/shared/features/form";
import { DocumentLanguageSchema, DocumentTypeSchema } from "@/features/document-templates/schemas";
import { DOCUMENT_LIMITS, type DocumentEditFormValues, type LegalEntityListItem } from "../schemas";

/** The legal entities as select options, by short name. */
export function legalEntityOptions(legalEntities: LegalEntityListItem[]): SelectOption[] {
  return legalEntities.map((legalEntity) => ({
    value: legalEntity.id,
    label: legalEntity.shortName,
  }));
}

/**
 * The fields a new and a stored document share: the legal entity that issues
 * it, the title, the type and the language. Rendered inside a form whose
 * values hold these four fields.
 */
export function DocumentDetailFields({
  legalEntities,
  isPending,
}: {
  legalEntities: LegalEntityListItem[];
  isPending: boolean;
}) {
  const t = useTranslations("documents");
  const tTemplates = useTranslations("documentTemplates");
  const { control } = useFormContext<DocumentEditFormValues>();

  const typeOptions = useMemo(
    () =>
      DocumentTypeSchema.options.map((value) => ({ value, label: tTemplates(`types.${value}`) })),
    [tTemplates]
  );
  const languageOptions = useMemo(
    () =>
      DocumentLanguageSchema.options.map((value) => ({
        value,
        label: tTemplates(`languages.${value}`),
      })),
    [tTemplates]
  );
  const entityOptions = useMemo(() => legalEntityOptions(legalEntities), [legalEntities]);

  return (
    <>
      <SelectField
        name="legalEntityId"
        label={t("form.legalEntity")}
        placeholder={t("form.legalEntityPlaceholder")}
        searchPlaceholder={t("form.searchPlaceholder")}
        control={control}
        options={entityOptions}
        required
        disabled={isPending}
      />
      <SelectField
        name="type"
        label={t("form.type")}
        control={control}
        options={typeOptions}
        required
        disabled={isPending}
      />
      <div className="md:col-span-2">
        <TextField
          name="title"
          label={t("form.title")}
          placeholder={t("form.titlePlaceholder")}
          control={control}
          required
          maxLength={DOCUMENT_LIMITS.titleMax}
          disabled={isPending}
        />
      </div>
      <SelectField
        name="language"
        label={t("form.language")}
        control={control}
        options={languageOptions}
        required
        disabled={isPending}
      />
    </>
  );
}
