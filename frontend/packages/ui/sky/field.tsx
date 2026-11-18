import * as React from "react";

import { cn } from "../src/lib/utils";

/** The look of every text field, select and text area, as the wall's filters use it. */
export const CONTROL_CLASS =
  "border-input bg-background min-w-0 rounded-md border px-2 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50 aria-invalid:border-destructive";

function Input({ className, ...props }: React.ComponentProps<"input">) {
  return (
    <input data-slot="input" className={cn(CONTROL_CLASS, "h-9 w-full", className)} {...props} />
  );
}

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(CONTROL_CLASS, "min-h-24 w-full py-2", className)}
      {...props}
    />
  );
}

function NativeSelect({ className, ...props }: React.ComponentProps<"select">) {
  return <select data-slot="select" className={cn(CONTROL_CLASS, "h-9", className)} {...props} />;
}

/** A label above its control, with an optional hint or error below. */
function Field({
  label,
  hint,
  error,
  children,
  className,
}: {
  label: string;
  hint?: React.ReactNode;
  error?: string | null;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <label className={cn("flex min-w-0 flex-col gap-1.5 text-sm", className)}>
      <span className="font-medium">{label}</span>
      {children}
      {error ? (
        <span className="text-error text-xs" role="alert">
          {error}
        </span>
      ) : hint ? (
        <span className="text-muted-foreground text-xs">{hint}</span>
      ) : null}
    </label>
  );
}

export { Field, Input, NativeSelect, Textarea };
