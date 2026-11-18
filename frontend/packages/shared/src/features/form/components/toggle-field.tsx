import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@oba/ui/components/form";
import { Switch } from "@oba/ui/components/switch";
import { cn } from "@oba/ui/lib/utils";
import { FieldValues } from "react-hook-form";
import { FormfieldProps } from "../types";

export type ToggleFieldProps<TFieldValues extends FieldValues = FieldValues> =
  FormfieldProps<TFieldValues> & {
    placement?: "LEFT" | "RIGHT";
    slotProps?: {
      switch?: Omit<React.ComponentProps<typeof Switch>, "checked" | "onCheckedChange">;
    };
  };

export function ToggleField<TFieldValues extends FieldValues = FieldValues>({
  name,
  description,
  label,
  control,
  required,
  tooltipText,
  placement = "RIGHT",
  slotProps,
}: ToggleFieldProps<TFieldValues>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field: { value, onChange, ...field }, fieldState: { error } }) => (
        <FormItem>
          <div className="flex items-center gap-1 leading-5">
            <FormLabel tooltipText={tooltipText}>{label}</FormLabel>
            {required ? <span className="text-destructive text-sm">*</span> : null}
          </div>
          <FormControl>
            <div
              className={cn(
                "border-input flex min-h-11 w-full items-center rounded-lg border-[1.5px] bg-transparent px-4 py-2 shadow-xs",
                error && "border-destructive",
                placement === "LEFT" ? "flex-row" : "flex-row-reverse"
              )}
            >
              <Switch
                checked={value}
                onCheckedChange={onChange}
                {...field}
                {...slotProps?.switch}
              />
              {description ? (
                <span
                  className={cn(
                    "text-muted-foreground flex-1 text-sm",
                    placement === "LEFT" ? "ml-3" : "mr-3"
                  )}
                >
                  {description}
                </span>
              ) : null}
            </div>
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
