"use client";

import { Link } from "@oba/shared/libs/i18n/navigation";
import { useTranslations } from "next-intl";
import { useMemo } from "react";
import { useFormContext } from "react-hook-form";
import { ArrowLeft } from "lucide-react";
import { Alert, AlertDescription } from "@oba/ui/sky/alert";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { PageHeading, PanelTitle } from "@/components/layout/page-heading";
import { Button } from "@oba/ui/sky/button";
import {
  TextField,
  TextareaField,
  SelectField,
  FormGrid,
  ObaForm,
} from "@oba/shared/features/form";
import { useCreateOrganization } from "@/features/organizations/hooks/use-organizations";
import {
  createOrganizationFormSchema,
  type OrganizationFormValues,
} from "@oba/shared/features/organizations/schemas";
import { toOrganizationRequest } from "@oba/shared/features/organizations/lib/organization-request";
import {
  getIndustryOptions,
  getCompanySizeOptions,
  getCountryOptions,
} from "@oba/shared/features/organizations/lib/organization-options";

/**
 * Create Organization Form (client)
 *
 * Form to create a new organization. Rendered by the thin
 * /organizations/new page.
 * On success `useCreateOrganization` refetches the cached queries and opens
 * the new organization in Admin. The active organization does not change.
 * The back link leads to the profile, which lists the user's organizations:
 * a user without an admin role has no organization list in Admin.
 *
 * There is no session-loading branch on purpose: the dashboard layout already
 * guarantees a session, and next-auth reports "loading" during every session
 * update, which would unmount the form and discard what was typed.
 */
export function CreateOrganizationForm() {
  const t = useTranslations("organization");
  const tNew = useTranslations("organization.new");
  const createOrg = useCreateOrganization();
  const schema = useMemo(() => createOrganizationFormSchema(t), [t]);

  const handleSubmit = (data: OrganizationFormValues) => {
    createOrg.mutate(toOrganizationRequest(data));
  };

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      {/* Back link */}
      <Link
        href="/"
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1.5 text-sm"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {tNew("backToProfile")}
      </Link>

      <PageHeading title={tNew("title")} description={tNew("description")} />

      {/* Error Alert */}
      {createOrg.isError && (
        <Alert variant="destructive" className="border-error/40 bg-error-light">
          <AlertDescription className="text-foreground">{tNew("createError")}</AlertDescription>
        </Alert>
      )}

      {/* Create Form */}
      <ObaForm
        schema={schema}
        onSubmit={handleSubmit}
        mode="onChange"
        defaultValues={{
          name: "",
          description: "",
          industry: "",
          companySize: "",
          country: "",
          website: "",
        }}
        className="space-y-6"
      >
        <CreateOrganizationFormFields isPending={createOrg.isPending} />
      </ObaForm>
    </div>
  );
}

function CreateOrganizationFormFields({ isPending }: { isPending: boolean }) {
  const t = useTranslations("organization");
  const tNew = useTranslations("organization.new");
  const tOnboarding = useTranslations("onboarding");
  const {
    control,
    formState: { isValid },
  } = useFormContext<OrganizationFormValues>();

  const industryOptions = getIndustryOptions(tOnboarding);
  const companySizeOptions = getCompanySizeOptions(tOnboarding);
  const countryOptions = getCountryOptions(tOnboarding);

  return (
    <>
      {/* Company Information */}
      <Card role="region" aria-labelledby="organization-company-info-heading">
        <CardHeader>
          <PanelTitle id="organization-company-info-heading">{t("companyInfo")}</PanelTitle>
        </CardHeader>
        <CardContent>
          <FormGrid className="md:grid-cols-2">
            <div className="md:col-span-2">
              <TextField
                name="name"
                label={t("name")}
                placeholder={t("namePlaceholder")}
                control={control}
                required
                disabled={isPending}
              />
            </div>
            <SelectField
              name="industry"
              label={t("industry")}
              placeholder={t("industryPlaceholder")}
              control={control}
              options={industryOptions}
              clearable
              disabled={isPending}
            />
            <SelectField
              name="companySize"
              label={t("companySize")}
              placeholder={t("companySizePlaceholder")}
              control={control}
              options={companySizeOptions}
              clearable
              disabled={isPending}
            />
            <SelectField
              name="country"
              label={t("country")}
              placeholder={t("countryPlaceholder")}
              control={control}
              options={countryOptions}
              clearable
              disabled={isPending}
            />
            <TextField
              name="website"
              label={t("website")}
              placeholder={t("websitePlaceholder")}
              control={control}
              disabled={isPending}
            />
            <div className="md:col-span-2">
              <TextareaField
                name="description"
                label={t("descriptionLabel")}
                placeholder={t("descriptionPlaceholder")}
                control={control}
                rows={3}
                disabled={isPending}
              />
            </div>
          </FormGrid>
        </CardContent>
      </Card>

      <Button type="submit" disabled={isPending || !isValid} className="w-full">
        {isPending ? tNew("submitting") : tNew("submit")}
      </Button>
    </>
  );
}
