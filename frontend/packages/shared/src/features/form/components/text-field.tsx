import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@oba/ui/components/form";

import { Input } from "@oba/ui/components/input";
import { FieldValues } from "react-hook-form";
import { FormfieldProps } from "../types";
import { useState } from "react";
import { Button } from "@oba/ui/components/button";
import { EyeIcon, EyeOffIcon } from "lucide-react";

export function TextField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  control,
  required,
  tooltipText,
  ...props
}: React.ComponentProps<typeof Input> & FormfieldProps<TFieldValues>) {
  const isPassword = props.type === "password";
  const [showPassword, setShowPassword] = useState(false);
  const togglePasswordVisibility = () => {
    setShowPassword(!showPassword);
  };

  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem className={!label ? "gap-0" : undefined}>
          {label && (
            <div className="flex items-center gap-1 leading-5">
              <FormLabel className="text-sm" tooltipText={tooltipText}>{label}</FormLabel>
              {required ? <span className="text-destructive">*</span> : null}
            </div>
          )}
          <FormControl>
            <div className="relative">
              <Input
                {...field}
                value={field.value ?? ""}
                onChange={(e) => {
                  const value = props.type === "number"
                    ? (Number.isNaN(e.target.valueAsNumber) ? null : e.target.valueAsNumber)
                    : e.target.value;
                  field.onChange(value);
                  props.onChange?.(e);
                }}
                {...props}
                type={isPassword ? (showPassword ? "text" : "password") : props.type}
              />
              {isPassword && field.value && (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="absolute top-1/2 right-2 -translate-y-1/2"
                  onClick={togglePasswordVisibility}
                >
                  {showPassword ? (
                    <EyeIcon className="h-4 w-4" />
                  ) : (
                    <EyeOffIcon className="h-4 w-4" />
                  )}
                </Button>
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
