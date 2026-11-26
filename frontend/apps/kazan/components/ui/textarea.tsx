import * as React from "react";

import { cn } from "@/lib/utils";

const Textarea = React.forwardRef<HTMLTextAreaElement, React.ComponentProps<"textarea">>(
  ({ className, ...props }, ref) => {
    return (
      <textarea
        data-slot="textarea"
        className={cn(
          "border-input bg-background dark:bg-input/30 text-foreground classic:rounded-lg classic:border-[1.5px] classic:shadow-xs classic:transition-all flex min-h-[60px] w-full border px-4 py-3 text-sm transition-colors duration-150",
          "placeholder:text-muted-foreground",
          "hover:border-border dark:hover:bg-input/50",
          "focus:border-primary classic:focus:ring-[3px] classic:focus:ring-primary/20 focus:outline-none",
          "disabled:bg-muted disabled:cursor-not-allowed disabled:opacity-50",
          className
        )}
        ref={ref}
        {...props}
      />
    );
  }
);
Textarea.displayName = "Textarea";

export { Textarea };
