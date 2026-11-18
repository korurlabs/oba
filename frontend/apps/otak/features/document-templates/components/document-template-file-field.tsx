"use client";

import type { Control, FieldPath, FieldValues } from "react-hook-form";
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@oba/ui/components/form";
import { Input } from "@oba/ui/sky/field";

interface DocumentTemplateFileFieldProps<T extends FieldValues> {
  name: FieldPath<T>;
  label: string;
  /** The picker's filter, such as `.html,text/html`. */
  accept: string;
  description?: string;
  control: Control<T>;
  disabled?: boolean;
}

/**
 * One labelled file picker bound to a `File | null` form value. The shared
 * form components have no file field, so this wraps the raw form field
 * around the sky input. The input stays uncontrolled, as a browser does not
 * let a page set a picked file; the form remounts per open to clear it.
 */
export function DocumentTemplateFileField<T extends FieldValues>({
  name,
  label,
  accept,
  description,
  control,
  disabled,
}: DocumentTemplateFileFieldProps<T>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel className="text-sm">{label}</FormLabel>
          <FormControl>
            <Input
              type="file"
              accept={accept}
              name={field.name}
              ref={field.ref}
              onBlur={field.onBlur}
              disabled={disabled}
              className="py-1.5"
              onChange={(event) => field.onChange(event.target.files?.[0] ?? null)}
            />
          </FormControl>
          {description ? <FormDescription>{description}</FormDescription> : null}
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
