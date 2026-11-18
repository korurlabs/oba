import { Combobox } from "@oba/ui/components/combobox";
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@oba/ui/components/form";
import { cn } from "@oba/ui/lib/utils";
import { FieldValues } from "react-hook-form";
import { FormfieldProps } from "../types";

type BaseComboboxProps = React.ComponentProps<typeof Combobox>;

export type ComboboxFieldOption = NonNullable<BaseComboboxProps["options"]>[number];

export type ComboboxFieldProps<TFieldValues extends FieldValues = FieldValues> = BaseComboboxProps &
  FormfieldProps<TFieldValues>;

export function ComboboxField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  control,
  onValueChange,
  required,
  tooltipText,
  ...props
}: ComboboxFieldProps<TFieldValues>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field: { onChange, ...field }, fieldState: { error } }) => (
        <FormItem>
          <div className="flex items-center gap-1 leading-5">
            <FormLabel tooltipText={tooltipText}>{label}</FormLabel>
            {required ? <span className="text-destructive text-sm">*</span> : null}
          </div>
          <FormControl>
            <Combobox
              className={cn(error && "border-destructive")}
              onValueChange={(value) => {
                onValueChange?.(value);
                onChange(value);
              }}
              {...field}
              {...props}
            />
          </FormControl>
          {description ? <FormDescription>{description}</FormDescription> : null}
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
