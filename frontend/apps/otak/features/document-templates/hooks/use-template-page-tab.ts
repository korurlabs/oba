"use client";

import { useQueryStates } from "nuqs";
import { templatePageTabParams } from "../lib/template-tabs";

/** The Templates page's tab, synced with the URL. */
export function useTemplatePageTab() {
  return useQueryStates(templatePageTabParams);
}
