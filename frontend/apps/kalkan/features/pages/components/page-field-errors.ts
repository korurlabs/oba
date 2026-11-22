import type { Path, UseFormReturn } from "react-hook-form";
import { isApiError } from "@oba/shared/api/axios";
import { validationFieldErrors } from "@/lib/api-error-text";
import { pageErrorText } from "../lib/page-error";
import type { PageFormValues } from "../schemas/page-form";

/** Refusals about the page's address, shown on the address field. */
const SLUG_ERROR_CODES = ["KLK_STA_001", "KLK_VAL_007"];

/** Server properties the form shows as one of its own fields. */
const FORM_FIELDS: readonly Path<PageFormValues>[] = ["slug", "parentId", "compliance"];

/**
 * Puts the field a refused save names on the form, beside the toast the
 * mutation shows. Returns true when the refusal named a field the form shows.
 * `t` is the `pages` translator.
 */
export function applyPageFieldErrors(
  form: UseFormReturn<PageFormValues>,
  error: unknown,
  t: (key: string) => string
): boolean {
  if (isApiError(error) && error.code !== undefined && SLUG_ERROR_CODES.includes(error.code)) {
    form.setError("slug", {
      type: "server",
      message: pageErrorText(error, t, "errors.validation"),
    });
    return true;
  }
  let applied = false;
  for (const [field, message] of Object.entries(validationFieldErrors(error))) {
    const formField = FORM_FIELDS.find((candidate) => candidate === field);
    if (formField !== undefined) {
      form.setError(formField, { type: "server", message });
      applied = true;
    }
  }
  return applied;
}
