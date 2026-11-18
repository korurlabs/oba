import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@oba/ui/components/form";

import { Checkbox } from "@oba/ui/components/checkbox";
import { FieldValues } from "react-hook-form";
import { FormfieldProps } from "../types";

export function CheckboxField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  control,
  required,
  tooltipText,
  ...props
}: React.ComponentProps<typeof Checkbox> & FormfieldProps<TFieldValues>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field: { value, onChange, ...field } }) => (
        <FormItem>
          <div className="flex items-start gap-2">
            <FormControl className="mt-1">
              <Checkbox checked={value} onCheckedChange={onChange} {...field} {...props} />
            </FormControl>
            <div className="flex flex-col items-start">
              <div className="flex items-center gap-1">
                <FormLabel tooltipText={tooltipText}>{label}</FormLabel>
                {required ? <span className="text-destructive text-sm">*</span> : null}
              </div>
              {description ? <FormDescription>{description}</FormDescription> : null}
            </div>
          </div>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
