"use client";

import * as React from "react";
import { format } from "date-fns";
import { CalendarIcon, X } from "lucide-react";
import type { Locale } from "date-fns";

import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Calendar } from "@/components/ui/calendar";
import { cn } from "@/lib/utils";

type DatePickerProps = {
  value?: Date;
  onChange?: (date: Date | undefined) => void;
  label?: string;
  required?: boolean;
  placeholder?: string;
  disabled?: boolean;
  locale?: Locale;
  /** date-fns format string, defaults to "PPP" (e.g. "Mar 30, 2026") */
  dateFormat?: string;
  /** Minimum selectable date */
  fromDate?: Date;
  /** Maximum selectable date */
  toDate?: Date;
  className?: string;
  /** Show a clear (X) button when a value is selected. Defaults to true. */
  isClearable?: boolean;
  /**
   * Forwarded to the trigger button. `FormControl` injects these through its
   * Slot, and they are what links a `FormLabel` (`htmlFor`) and the field's
   * description / error message to the control.
   */
  id?: string;
  "aria-describedby"?: string;
  "aria-invalid"?: boolean;
};

const DEFAULT_START_MONTH = new Date(new Date().getFullYear() - 10, 0);
const DEFAULT_END_MONTH = new Date(new Date().getFullYear() + 10, 11);

function DatePicker({
  value,
  onChange,
  label,
  required,
  placeholder,
  disabled = false,
  locale,
  dateFormat = "PPP",
  fromDate,
  toDate,
  className,
  isClearable = true,
  id,
  "aria-describedby": ariaDescribedBy,
  "aria-invalid": ariaInvalid,
}: DatePickerProps) {
  const [open, setOpen] = React.useState(false);

  const handleSelect = (date: Date | undefined) => {
    onChange?.(date);
    setOpen(false);
  };

  const formattedDate = value
    ? format(value, dateFormat, { locale })
    : undefined;

  return (
    <div className="space-y-2">
      {label && (
        <div className="flex items-center gap-1 leading-5">
          <Label>{label}</Label>
          {required ? <span className="text-destructive">*</span> : null}
        </div>
      )}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            id={id}
            aria-describedby={ariaDescribedBy}
            aria-invalid={ariaInvalid}
            type="button"
            variant="outline"
            disabled={disabled}
            className={cn(
              "flex h-11 w-full items-center justify-between whitespace-nowrap rounded-lg border-[1.5px] border-input bg-background px-4 py-2 text-sm text-foreground shadow-xs transition-all duration-150 dark:bg-input/30",
              "hover:border-border dark:hover:bg-input/50",
              "outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/20",
              open && "border-primary ring-[3px] ring-primary/20",
              "disabled:cursor-not-allowed disabled:opacity-50 disabled:bg-muted",
              !formattedDate && "text-muted-foreground",
              className
            )}
          >
            <span className="truncate">
              {formattedDate ?? placeholder}
            </span>
            <div className="ml-2 flex items-center gap-1">
              {isClearable && value && (
                <span
                  role="button"
                  tabIndex={-1}
                  onClick={(e) => {
                    e.stopPropagation();
                    onChange?.(undefined);
                  }}
                  className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-muted-foreground hover:text-foreground hover:bg-muted"
                >
                  <X className="h-3 w-3" />
                </span>
              )}
              <CalendarIcon className="h-4 w-4 shrink-0 text-muted-foreground" />
            </div>
          </Button>
        </PopoverTrigger>
        <PopoverContent
          className="w-auto p-0"
          align="start"
          sideOffset={8}
        >
          <Calendar
            mode="single"
            captionLayout="dropdown"
            selected={value}
            onSelect={handleSelect}
            locale={locale}
            startMonth={fromDate ?? DEFAULT_START_MONTH}
            endMonth={toDate ?? DEFAULT_END_MONTH}
            disabled={[
              ...(fromDate ? [{ before: fromDate }] : []),
              ...(toDate ? [{ after: toDate }] : []),
            ]}
            defaultMonth={value}
          />
        </PopoverContent>
      </Popover>
    </div>
  );
}

export { DatePicker };
export type { DatePickerProps };
