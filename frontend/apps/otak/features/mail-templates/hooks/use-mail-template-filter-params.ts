"use client";

import { useQueryStates } from "nuqs";
import { mailTemplateFilterParams } from "../lib/mail-template-filters";

export type { MailTemplateFilterParams } from "../lib/mail-template-filters";

/** The mail template list's search, language and page, synced with the URL. */
export function useMailTemplateFilterParams() {
  return useQueryStates(mailTemplateFilterParams);
}
