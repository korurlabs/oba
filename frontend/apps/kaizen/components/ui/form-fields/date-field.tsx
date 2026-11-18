"use client";

import { FormField } from "@/components/ui/form";
import {
  FormFieldWrapper,
  type FormFieldLayoutProps,
} from "@/components/ui/form-fields/form-field-wrapper";
import { DatePicker, type DatePickerProps } from "@/components/ui/date-picker";
import { FieldValues, Control, Path } from "react-hook-form";
import { useLocale } from "next-intl";
import { getDateFnsLocale } from "@oba/shared/lib/date-fns-locale";

type DateFieldProps<TFieldValues extends FieldValues = FieldValues> = Omit<
  DatePickerProps,
  "value" | "onChange"
> &
  FormFieldLayoutProps & {
    name: Path<TFieldValues>;
    label?: string;
    description?: string;
    control: Control<TFieldValues>;
    required?: boolean;
  };

export function DateField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  control,
  required,
  orientation,
  tooltipText,
  ...props
}: DateFieldProps<TFieldValues>) {
  const locale = useLocale();
  return (
    <FormField
      control={control}
      name={name}
      render={({ field: { value, onChange } }) => (
        <FormFieldWrapper
          label={label}
          required={required}
          description={description}
          orientation={orientation}
          tooltipText={tooltipText}
        >
          <DatePicker
            locale={getDateFnsLocale(locale)}
            value={value as Date | undefined}
            onChange={onChange}
            {...props}
          />
        </FormFieldWrapper>
      )}
    />
  );
}
