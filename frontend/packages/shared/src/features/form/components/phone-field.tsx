"use client";

import { useCallback, useRef } from "react";
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@oba/ui/components/form";
import { PhoneInput, type SearchInputRenderProps } from "@oba/ui/components/phone-input";
import { Label } from "@oba/ui/components/label";
import { FieldValues } from "react-hook-form";
import {
  parsePhoneNumber,
  getCountryCallingCode,
} from "react-phone-number-input";
import type { Country, Value } from "react-phone-number-input";
import { FormfieldProps } from "../types";
import { SenseySearchInput } from "../../../components/sensey-search-input";

// ── Types ────────────────────────────────────────────────────────────

export type PhoneValue = {
  country: string | null;
  dialCode: string | null;
  phoneNumber: string | null;
};

type PhoneFieldBaseProps = Omit<
  React.ComponentProps<typeof PhoneInput>,
  "value" | "onChange" | "onCountryChange"
> & {
  /** Default country for the country selector (defaults to "NL") */
  defaultCountry?: Country;
};

// ── Helpers ──────────────────────────────────────────────────────────

/**
 * Parse an E.164 phone string into a structured PhoneValue.
 * Returns null fields if parsing fails.
 */
function parseE164ToPhoneValue(e164: string | null | undefined): PhoneValue {
  if (!e164) {
    return { country: null, dialCode: null, phoneNumber: null };
  }

  try {
    const parsed = parsePhoneNumber(e164);
    if (parsed) {
      return {
        country: parsed.country ?? null,
        dialCode: parsed.country
          ? `+${getCountryCallingCode(parsed.country)}`
          : null,
        phoneNumber: parsed.nationalNumber ?? null,
      };
    }
  } catch {
    // Fall through to default
  }

  return { country: null, dialCode: null, phoneNumber: e164 };
}

/**
 * Build a PhoneValue from the current country and E.164 value.
 */
function buildPhoneValue(
  country: Country | undefined,
  e164Value: Value | undefined,
): PhoneValue {
  const dialCode = country
    ? `+${getCountryCallingCode(country)}`
    : null;

  if (!e164Value) {
    return { country: country ?? null, dialCode, phoneNumber: null };
  }

  try {
    const parsed = parsePhoneNumber(e164Value);
    if (parsed) {
      return {
        country: country ?? parsed.country ?? null,
        dialCode: dialCode ?? (parsed.country ? `+${getCountryCallingCode(parsed.country)}` : null),
        phoneNumber: parsed.nationalNumber ?? null,
      };
    }
  } catch {
    // Fall through
  }

  // e164Value may already contain the dial code (e.g. "+315" for partial NL input).
  // Strip it to avoid double-prefixing when phoneValueToE164 re-adds it.
  const nationalNumber = dialCode && e164Value.startsWith(dialCode)
    ? e164Value.slice(dialCode.length)
    : e164Value;

  return {
    country: country ?? null,
    dialCode,
    phoneNumber: nationalNumber || null,
  };
}

/**
 * Convert a PhoneValue back to an E.164 string for the underlying PhoneInput.
 */
function phoneValueToE164(phoneValue: PhoneValue | null | undefined): string {
  if (!phoneValue) return "";
  if (!phoneValue.phoneNumber) return "";
  if (phoneValue.dialCode) {
    return `${phoneValue.dialCode}${phoneValue.phoneNumber}`;
  }
  return phoneValue.phoneNumber;
}

/**
 * Extract the Country from a PhoneValue for the defaultCountry prop.
 */
function phoneValueToCountry(
  phoneValue: PhoneValue | null | undefined,
  fallback: Country,
): Country {
  if (phoneValue?.country) {
    return phoneValue.country as Country;
  }
  return fallback;
}

// ── Search Input Renderer ───────────────────────────────────────────

function renderSearchInput({ value, onChange, placeholder }: SearchInputRenderProps) {
  return (
    <SenseySearchInput
      value={value}
      onSearchChange={(searchValue) => onChange(searchValue ?? "")}
      placeholder={placeholder}
      size="sm"
    />
  );
}

// ── Standalone (no form context) ─────────────────────────────────────

type PhoneFieldStandaloneProps = PhoneFieldBaseProps & {
  value: PhoneValue | null;
  onChange: (value: PhoneValue) => void;
  label?: string;
  required?: boolean;
  disabled?: boolean;
};

export function PhoneFieldStandalone({
  value,
  onChange,
  label,
  required,
  defaultCountry = "NL",
  disabled,
  ...props
}: PhoneFieldStandaloneProps) {
  const countryRef = useRef<Country | undefined>(
    phoneValueToCountry(value, defaultCountry),
  );

  const handleCountryChange = useCallback(
    (country?: Country) => {
      countryRef.current = country;
    },
    [],
  );

  const handleChange = useCallback(
    (e164Value?: Value) => {
      const phoneValue = buildPhoneValue(countryRef.current, e164Value);
      onChange(phoneValue);
    },
    [onChange],
  );

  const e164String = phoneValueToE164(value);
  const resolvedCountry = phoneValueToCountry(value, defaultCountry);

  return (
    <div className="flex w-full flex-col gap-2">
      {label && (
        <div className="flex items-center gap-1 leading-5">
          <Label>{label}</Label>
          {required ? <span className="text-destructive">*</span> : null}
        </div>
      )}
      <PhoneInput
        value={(e164String || undefined) as Value | undefined}
        onChange={handleChange}
        onCountryChange={handleCountryChange}
        defaultCountry={resolvedCountry}
        disabled={disabled}
        renderSearchInput={renderSearchInput}
        {...props}
      />
    </div>
  );
}

// ── Form-integrated PhoneField ───────────────────────────────────────

type PhoneFieldProps<TFieldValues extends FieldValues = FieldValues> =
  FormfieldProps<TFieldValues> & PhoneFieldBaseProps;

export function PhoneField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  control,
  required,
  tooltipText,
  defaultCountry = "NL",
  ...props
}: PhoneFieldProps<TFieldValues>) {
  const countryRef = useRef<Country | undefined>(undefined);

  const handleCountryChange = useCallback((country?: Country) => {
    countryRef.current = country;
  }, []);

  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => {
        const currentValue = field.value as PhoneValue | string | null | undefined;

        // Support both PhoneValue object and legacy E.164 string
        const resolvedValue: PhoneValue =
          typeof currentValue === "string"
            ? parseE164ToPhoneValue(currentValue)
            : currentValue ?? { country: null, dialCode: null, phoneNumber: null };

        const e164String = phoneValueToE164(resolvedValue);
        const resolvedCountry = phoneValueToCountry(resolvedValue, defaultCountry);

        // Initialize the country ref from the resolved value
        if (!countryRef.current && resolvedValue.country) {
          countryRef.current = resolvedValue.country as Country;
        }

        const handleChange = (e164Value?: Value) => {
          const phoneValue = buildPhoneValue(countryRef.current, e164Value);
          field.onChange(phoneValue);
        };

        return (
          <FormItem>
            {label && (
              <div className="flex items-center gap-1 leading-5">
                <FormLabel tooltipText={tooltipText}>{label}</FormLabel>
                {required ? (
                  <span className="text-destructive text-sm">*</span>
                ) : null}
              </div>
            )}
            <FormControl>
              <PhoneInput
                value={(e164String || undefined) as Value | undefined}
                onChange={handleChange}
                onCountryChange={handleCountryChange}
                defaultCountry={resolvedCountry}
                renderSearchInput={renderSearchInput}
                {...props}
              />
            </FormControl>
            {description ? (
              <FormDescription>{description}</FormDescription>
            ) : null}
            <FormMessage />
          </FormItem>
        );
      }}
    />
  );
}
