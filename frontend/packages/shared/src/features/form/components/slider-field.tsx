import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@oba/ui/components/form";
import { Slider } from "@oba/ui/components/slider";
import { FieldValues } from "react-hook-form";
import { FormfieldProps } from "../types";

export type SliderFieldProps<TFieldValues extends FieldValues = FieldValues> =
  FormfieldProps<TFieldValues> & {
    min?: number;
    max?: number;
    step?: number;
    showValue?: boolean;
    valueSuffix?: string;
  };

export function SliderField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  control,
  required,
  tooltipText,
  min = 0,
  max = 100,
  step = 1,
  showValue = true,
  valueSuffix,
}: SliderFieldProps<TFieldValues>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field: { value, onChange, ...field } }) => (
        <FormItem>
          <div className="flex items-center gap-1 leading-5">
            <FormLabel tooltipText={tooltipText}>{label}</FormLabel>
            {required ? <span className="text-destructive text-sm">*</span> : null}
          </div>
          <FormControl>
            <div className="flex items-center gap-3">
              <Slider
                min={min}
                max={max}
                step={step}
                value={[value ?? min]}
                onValueChange={(values) => onChange(values[0])}
                {...field}
              />
              {showValue && (
                <span className="text-sm font-medium tabular-nums min-w-[3ch] text-right">
                  {value ?? min}
                  {valueSuffix}
                </span>
              )}
            </div>
          </FormControl>
          {description ? <FormDescription>{description}</FormDescription> : null}
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
