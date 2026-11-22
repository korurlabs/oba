"use client";

import { useCallback } from "react";
import { useTranslations } from "next-intl";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import type { DocumentUser } from "../schemas";

/**
 * The text of a document's last change, "Today by Merthan Tukus" or "Nov 3,
 * 2026 by ...": today and yesterday by name, an older change by its day in
 * the viewer's zone. A user who is gone leaves the day alone.
 */
export function useEditedText() {
  const t = useTranslations("documents");
  const { formatDate, dayKey, relativeDay } = useDateFormat();

  return useCallback(
    (updatedAt: string, updatedBy: DocumentUser | null): string => {
      const recent = relativeDay(dayKey(updatedAt));
      const time = recent ? t(`edited.${recent}`) : formatDate(updatedAt);
      const name = updatedBy?.displayName;
      return name ? t("edited.by", { time, name }) : time;
    },
    [t, formatDate, dayKey, relativeDay]
  );
}
