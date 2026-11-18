"use client";

import { Info } from "lucide-react";
import {
  FormControl,
  FormDescription,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

/**
 * `vertical` puts the label above the control (the default everywhere).
 * `horizontal` is a label-left / value-right row for dense property panels.
 */
export type FormFieldOrientation = "vertical" | "horizontal";

/**
 * Grid for a horizontal row. The 7rem label column must stay in step with
 * the horizontal `SelectField` in `@oba/shared/features/form`, so that
 * rows from both packages line up when they share a panel.
 */
const HORIZONTAL_ITEM_CLASS =
  "grid grid-cols-[7rem_minmax(0,1fr)] items-center gap-x-3 gap-y-1 space-y-0";

export type FormFieldLayoutProps = {
  /** Label position. Defaults to `vertical`. */
  orientation?: FormFieldOrientation;
  /** Shown in a tooltip behind an info icon next to the label. */
  tooltipText?: string;
};

type FormFieldWrapperProps = FormFieldLayoutProps & {
  label?: string;
  required?: boolean;
  description?: string;
  children: React.ReactNode;
  className?: string;
};

export function FormFieldWrapper({
  label,
  required,
  description,
  children,
  className,
  orientation = "vertical",
  tooltipText,
}: FormFieldWrapperProps) {
  const isHorizontal = orientation === "horizontal";

  return (
    <FormItem className={cn(isHorizontal && HORIZONTAL_ITEM_CLASS, className)}>
      {label && (
        <div className="flex min-w-0 items-center gap-1 leading-5">
          <FormLabel
            className={cn(
              // A narrow label column has to be allowed to wrap (nl
              // "Bovenliggend Ticket", tr "Gözden Geçiren"), so the
              // single-line `leading-none` of the base label is relaxed.
              isHorizontal && "text-muted-foreground font-normal leading-snug",
            )}
          >
            {label}
          </FormLabel>
          {/* `leading-none` like the label: with the row's 20px line height
              the marker made a required label row 6px taller than an
              optional one, so side-by-side fields did not line up. */}
          {required ? <span className="text-destructive text-sm leading-none">*</span> : null}
          {tooltipText ? <LabelTooltip text={tooltipText} /> : null}
        </div>
      )}
      <FormControl>{children}</FormControl>
      {description ? (
        <FormDescription className={cn(isHorizontal && "col-start-2")}>
          {description}
        </FormDescription>
      ) : null}
      <FormMessage className={cn(isHorizontal && "col-start-2")} />
    </FormItem>
  );
}

/**
 * Info icon with the hint in a tooltip. A real button, so keyboard users can
 * reach the hint too (the hint usually explains why the control is disabled).
 * Brings its own provider: the app has no app-wide `TooltipProvider`.
 */
function LabelTooltip({ text }: { text: string }) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            type="button"
            aria-label={text}
            className="text-muted-foreground hover:text-foreground focus-visible:ring-primary/40 inline-flex shrink-0 items-center rounded-sm outline-none focus-visible:ring-2"
          >
            <Info className="h-3.5 w-3.5" />
          </button>
        </TooltipTrigger>
        <TooltipContent side="top" className="max-w-64">
          {text}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
