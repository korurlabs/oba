"use client";

import { useTranslations } from "next-intl";
import { LoadError } from "@oba/ui/sky/page-states";

interface OrganizationLoadErrorProps {
  onRetry: () => void;
}

/**
 * Shown in place of organization data when its query failed (the current
 * user, the groups of the organization, the members of a group, the app
 * grants, the effective app access), with a retry of that query.
 */
export function OrganizationLoadError({ onRetry }: OrganizationLoadErrorProps) {
  const t = useTranslations("errors");

  return (
    <LoadError
      title={t("somethingWentWrong")}
      message={t("unexpectedError")}
      onRetry={onRetry}
      retryLabel={t("tryAgain")}
    />
  );
}
