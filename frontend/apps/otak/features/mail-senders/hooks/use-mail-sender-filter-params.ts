"use client";

import { useQueryStates } from "nuqs";
import { mailSenderFilterParams } from "../lib/mail-sender-filters";

export type { MailSenderFilterParams } from "../lib/mail-sender-filters";

/** The sender list's search, synced with the URL. */
export function useMailSenderFilterParams() {
  return useQueryStates(mailSenderFilterParams);
}
