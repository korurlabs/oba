import type { FieldValues, Path, UseFormReturn } from "react-hook-form";
import { documentFieldErrors } from "../lib/document-error";

/** Puts the fields a refused save or upload names on the form; other fields are left to the toast. */
export function applyDocumentFieldErrors<T extends FieldValues>(
  form: UseFormReturn<T>,
  error: unknown,
  t: (key: string) => string
): void {
  const fields = Object.keys(form.getValues());
  for (const [field, message] of Object.entries(documentFieldErrors(error, t))) {
    if (fields.includes(field)) {
      form.setError(field as Path<T>, { type: "server", message });
    }
  }
}
