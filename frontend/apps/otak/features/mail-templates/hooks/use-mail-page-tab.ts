"use client";

import { useQueryStates } from "nuqs";
import { mailPageTabParams } from "../lib/mail-page-tabs";

/** The Mail page's tab, synced with the URL. */
export function useMailPageTab() {
  return useQueryStates(mailPageTabParams);
}
