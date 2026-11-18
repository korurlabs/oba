"use client";

import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FormField } from "@/components/ui/form";
import {
  FormFieldWrapper,
  type FormFieldLayoutProps,
} from "@/components/ui/form-fields/form-field-wrapper";
import { FieldValues, Control, Path } from "react-hook-form";

type SelectFieldProps<TFieldValues extends FieldValues = FieldValues> =
  FormFieldLayoutProps & {
    name: Path<TFieldValues>;
    label?: string;
    description?: string;
    control: Control<TFieldValues>;
    required?: boolean;
    placeholder?: string;
    disabled?: boolean;
    /** Extra classes for the trigger, e.g. a smaller height than the default `h-11`. */
    triggerClassName?: string;
    options: {
      label: React.ReactNode;
      value: string;
    }[];
  };

export function SelectField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  control,
  required,
  placeholder,
  disabled,
  triggerClassName,
  orientation,
  tooltipText,
  options,
}: SelectFieldProps<TFieldValues>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field: { value, onChange, ...field } }) => (
        <FormFieldWrapper
          label={label}
          required={required}
          description={description}
          orientation={orientation}
          tooltipText={tooltipText}
        >
          <Select value={value} onValueChange={onChange} disabled={disabled}>
            <SelectTrigger {...field} className={triggerClassName}>
              <SelectValue placeholder={placeholder} />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {options.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </FormFieldWrapper>
      )}
    />
  );
}
