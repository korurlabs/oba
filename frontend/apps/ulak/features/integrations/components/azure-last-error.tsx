"use client";

import { useTranslations } from "next-intl";
import { parseAzureLastError } from "../lib/azure-last-error";

/**
 * The reason of the last failed check in words. The API stores only codes,
 * never Azure's message: a known code has its own text, Entra's AADSTS code
 * is added when Entra sent one, and an unknown code is named as it is.
 */
export function AzureLastError({ lastError }: { lastError: string | null }) {
  const t = useTranslations("integrations.azure");
  const parsed = parseAzureLastError(lastError);
  if (!parsed) {
    return <span className="text-muted-foreground text-sm">{t("noError")}</span>;
  }

  return (
    <span className="flex flex-col gap-0.5 text-sm">
      <span>
        {parsed.known
          ? t(`lastErrors.${parsed.code}`)
          : t("lastErrors.unknown", { code: parsed.code })}
      </span>
      {parsed.entraCode && (
        <span className="text-muted-foreground font-mono text-xs">
          {t("lastErrors.entraCode", { code: parsed.entraCode })}
        </span>
      )}
    </span>
  );
}
