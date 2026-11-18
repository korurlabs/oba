import { useMemo } from "react";

import { SUPPORTED_LANGUAGES, useTranslation, type SupportedLanguage } from "@/i18n";
import { createDates, type DateFormatter } from "@/lib/dates";

function asSupportedLanguage(language: string): SupportedLanguage {
  return (SUPPORTED_LANGUAGES as readonly string[]).includes(language)
    ? (language as SupportedLanguage)
    : "en";
}

/** The date formatter in the app language and the device zone, for render code. */
export function useDates(): DateFormatter {
  const { t, language } = useTranslation();
  return useMemo(() => createDates({ language: asSupportedLanguage(language), t }), [language, t]);
}
