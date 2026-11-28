import type { ReactNode } from "react";

import { cn } from "../src/lib/utils";

/** A labelled control of a filter row, with its label above it in Kaya's small caption. */
export function FilterLabel({
  label,
  htmlFor,
  children,
  className,
}: {
  label: string;
  /** The id of the control, so a click on the label focuses it. */
  htmlFor?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex min-w-0 flex-col gap-1 text-xs font-medium", className)}>
      <label htmlFor={htmlFor} className="text-muted-foreground">
        {label}
      </label>
      {children}
    </div>
  );
}
