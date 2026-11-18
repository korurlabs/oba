import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@oba/ui/components/form";
import { Textarea } from "@oba/ui/components/textarea";
import { FieldValues } from "react-hook-form";
import { FormfieldProps } from "../types";

export function TextareaField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  control,
  required,
  tooltipText,
  ...props
}: React.ComponentProps<typeof Textarea> & FormfieldProps<TFieldValues>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <div className="flex items-center gap-1 leading-5">
            <FormLabel tooltipText={tooltipText}>{label}</FormLabel>
            {required ? <span className="text-destructive text-sm">*</span> : null}
          </div>
          <FormControl>
            <Textarea {...field} {...props} />
          </FormControl>
         {description ? <FormDescription>{description}</FormDescription> : null}
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
