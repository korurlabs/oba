"use client";

import { useMemo, type ReactNode } from "react";
import { useFormContext } from "react-hook-form";
import { useTranslations } from "next-intl";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader, CardTitle } from "@oba/ui/sky/card";
import {
  TextField,
  TextareaField,
  SelectField,
  FormGrid,
  ObaForm,
} from "@oba/shared/features/form";
import { useUpdateOrganization } from "@/features/organizations/hooks/use-organizations";
import {
  createOrganizationFormSchema,
  type OrganizationDetail,
  type OrganizationFormValues,
} from "@oba/shared/features/organizations/schemas";
import { toOrganizationRequest } from "@oba/shared/features/organizations/lib/organization-request";
import {
  getIndustryOptions,
  getCompanySizeOptions,
  getCountryOptions,
} from "@oba/shared/features/organizations/lib/organization-options";

interface OrganizationDetailFormProps {
  organizationId: string;
  orgData: OrganizationDetail | undefined;
  canEdit: boolean;
}

export function OrganizationDetailForm({
  organizationId,
  orgData,
  canEdit,
}: OrganizationDetailFormProps) {
  const t = useTranslations("organization");
  const { formatDate } = useDateFormat();
  const schema = useMemo(() => createOrganizationFormSchema(t), [t]);
  const updateOrg = useUpdateOrganization(organizationId);

  const handleSubmit = (data: OrganizationFormValues) => {
    updateOrg.mutate(toOrganizationRequest(data));
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
      <Card>
        <CardHeader>
          <CardTitle>{t("companyInfo")}</CardTitle>
        </CardHeader>
        <CardContent>
          <ObaForm
            schema={schema}
            onSubmit={handleSubmit}
            values={
              orgData
                ? {
                    name: orgData.name ?? "",
                    description: orgData.description ?? "",
                    industry: orgData.industry ?? "",
                    companySize: orgData.companySize ?? "",
                    country: orgData.country ?? "",
                    website: orgData.website ?? "",
                  }
                : undefined
            }
            className="flex flex-col gap-6"
          >
            <OrganizationFormFields canEdit={canEdit} isPending={updateOrg.isPending} />
          </ObaForm>
        </CardContent>
      </Card>

      {/* Organization facts: status and record dates, as Kaya's detail cards list them */}
      <Card className="self-start">
        <CardHeader>
          <CardTitle>{t("record")}</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="divide-y">
            <FactRow label={t("status")}>
              <Badge variant={orgData?.status === "DISABLED" ? "error" : "success"}>
                {orgData?.status ? t(`statuses.${orgData.status}`) : "-"}
              </Badge>
            </FactRow>
            <FactRow label={t("createdAt")}>
              {orgData?.createdAt ? formatDate(orgData.createdAt) : "-"}
            </FactRow>
            <FactRow label={t("updatedAt")}>
              {orgData?.updatedAt ? formatDate(orgData.updatedAt) : "-"}
            </FactRow>
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}

function FactRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 py-2">
      <dt className="text-muted-foreground text-sm">{label}</dt>
      <dd className="text-sm tabular-nums">{children}</dd>
    </div>
  );
}

function OrganizationFormFields({
  canEdit,
  isPending,
}: {
  canEdit: boolean;
  isPending: boolean;
}) {
  const t = useTranslations("organization");
  const tOnboarding = useTranslations("onboarding");
  const { control, formState } = useFormContext<OrganizationFormValues>();
  const { isDirty, isValid } = formState;

  const industryOptions = getIndustryOptions(tOnboarding);
  const companySizeOptions = getCompanySizeOptions(tOnboarding);
  const countryOptions = getCountryOptions(tOnboarding);

  return (
    <>
      <div>
        <FormGrid className="md:grid-cols-2">
          <div className="md:col-span-2">
            <TextField
              name="name"
              label={t("name")}
              placeholder={t("namePlaceholder")}
              control={control}
              required
              disabled={!canEdit || isPending}
            />
          </div>
          <SelectField
            name="industry"
            label={t("industry")}
            placeholder={t("industryPlaceholder")}
            control={control}
            options={industryOptions}
            clearable
            disabled={!canEdit || isPending}
          />
          <SelectField
            name="companySize"
            label={t("companySize")}
            placeholder={t("companySizePlaceholder")}
            control={control}
            options={companySizeOptions}
            clearable
            disabled={!canEdit || isPending}
          />
          <SelectField
            name="country"
            label={t("country")}
            placeholder={t("countryPlaceholder")}
            control={control}
            options={countryOptions}
            clearable
            disabled={!canEdit || isPending}
          />
          <TextField
            name="website"
            label={t("website")}
            placeholder={t("websitePlaceholder")}
            control={control}
            disabled={!canEdit || isPending}
          />
          <div className="md:col-span-2">
            <TextareaField
              name="description"
              label={t("descriptionLabel")}
              placeholder={t("descriptionPlaceholder")}
              control={control}
              rows={3}
              disabled={!canEdit || isPending}
            />
          </div>
        </FormGrid>
      </div>

      <div className="flex justify-end border-t pt-4">
        {canEdit ? (
          <Button type="submit" disabled={isPending || !isDirty || !isValid}>
            {isPending ? t("saving") : t("saveChanges")}
          </Button>
        ) : (
          <p className="text-muted-foreground text-sm">{t("readOnlyNote")}</p>
        )}
      </div>
    </>
  );
}
