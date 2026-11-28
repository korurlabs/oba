import * as React from "react";

import { cn } from "@/lib/utils";

export interface InputProps extends React.ComponentProps<"input"> {
  error?: boolean;
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, error, ...props }, ref) => {
    return (
      <input
        data-slot="input"
        type={type}
        className={cn(
          "border-input bg-background dark:bg-input/30 text-foreground classic:rounded-lg classic:border-[1.5px] classic:shadow-xs classic:transition-all flex h-11 w-full border px-4 py-3 text-sm transition-colors duration-150",
          "placeholder:text-muted-foreground",
          "hover:border-border dark:hover:bg-input/50",
          "focus:border-primary classic:focus:ring-[3px] classic:focus:ring-primary/20 focus:outline-none",
          "disabled:bg-muted disabled:cursor-not-allowed disabled:opacity-50",
          "file:text-foreground file:border-0 file:bg-transparent file:text-sm file:font-medium",
          error && "border-error focus:border-error",
          className
        )}
        aria-invalid={error || undefined}
        ref={ref}
        {...props}
      />
    );
  }
);
Input.displayName = "Input";

export { Input };
