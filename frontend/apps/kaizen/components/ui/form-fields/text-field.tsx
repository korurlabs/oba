"use client";

import { Input } from "@/components/ui/input";
import { FormField } from "@/components/ui/form";
import {
  FormFieldWrapper,
  type FormFieldLayoutProps,
} from "@/components/ui/form-fields/form-field-wrapper";
import { FieldValues, Control, Path } from "react-hook-form";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { EyeIcon, EyeOffIcon } from "lucide-react";

type TextFieldProps<TFieldValues extends FieldValues = FieldValues> =
  React.ComponentProps<typeof Input> &
    FormFieldLayoutProps & {
      name: Path<TFieldValues>;
      label?: string;
      description?: string;
      control: Control<TFieldValues>;
      required?: boolean;
      /**
       * Muted fixed text shown inside the input on the right, e.g. `%` or the
       * round " 2" after a series name. Not part of the value.
       */
      suffix?: string;
    };

export function TextField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  control,
  required,
  orientation,
  tooltipText,
  suffix,
  ...props
}: TextFieldProps<TFieldValues>) {
  const isPassword = props.type === "password";
  const [showPassword, setShowPassword] = useState(false);

  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormFieldWrapper
          label={label}
          required={required}
          description={description}
          orientation={orientation}
          tooltipText={tooltipText}
        >
          <div className="relative">
            <Input
              {...field}
              value={field.value ?? ""}
              onChange={(e) => {
                field.onChange(e.target.value);
                props.onChange?.(e);
              }}
              {...props}
              className={cn(props.className, suffix && "pr-8")}
              // `pr-8` fits one character; a longer suffix gets room for
              // every character so the typed text never runs under it.
              style={
                suffix && suffix.length > 1
                  ? { ...props.style, paddingRight: `calc(${suffix.length}ch + 1rem)` }
                  : props.style
              }
              type={isPassword ? (showPassword ? "text" : "password") : props.type}
            />
            {suffix ? (
              <span
                aria-hidden="true"
                className="text-muted-foreground pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-sm whitespace-pre"
              >
                {suffix}
              </span>
            ) : null}
            {isPassword && field.value && (
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                className="absolute top-1/2 right-2 -translate-y-1/2"
                onClick={() => setShowPassword(!showPassword)}
              >
                {showPassword ? (
                  <EyeIcon className="h-4 w-4" />
                ) : (
                  <EyeOffIcon className="h-4 w-4" />
                )}
              </Button>
            )}
          </div>
        </FormFieldWrapper>
      )}
    />
  );
}
