"use client";

import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/components/ui/form";
import { FormFieldWrapper } from "@/components/ui/form-fields/form-field-wrapper";
import { FieldValues, Control, Path } from "react-hook-form";

type TextareaFieldProps<TFieldValues extends FieldValues = FieldValues> =
  React.ComponentProps<typeof Textarea> & {
    name: Path<TFieldValues>;
    label?: string;
    description?: string;
    control: Control<TFieldValues>;
    required?: boolean;
  };

export function TextareaField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  control,
  required,
  ...props
}: TextareaFieldProps<TFieldValues>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormFieldWrapper
          label={label}
          required={required}
          description={description}
        >
          <Textarea {...field} value={field.value ?? ""} {...props} />
        </FormFieldWrapper>
      )}
    />
  );
}
