import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

/*
 * A notice in the grid look: a card-coloured plate with a hairline rim and a
 * 2px leading rule. The rule and the icon carry the tone (primary for
 * information, ink for a warning, red for danger), never a tinted fill.
 */
const inlineAlertVariants = cva(
  "flex flex-row items-center gap-2 border border-l-2 bg-card p-3 classic:rounded-lg classic:border-l",
  {
    variants: {
      variant: {
        info: "border-primary/30 border-l-primary [&_svg]:text-primary classic:border-info/30 classic:bg-info/10 classic:[&_svg]:text-info",
        success:
          "border-primary/30 border-l-korur-blue-strong [&_svg]:text-korur-blue-strong classic:border-success/30 classic:bg-success/10 classic:[&_svg]:text-success",
        warning:
          "border-foreground/25 border-l-foreground/70 [&_svg]:text-foreground classic:border-warning/30 classic:bg-warning/10 classic:[&_svg]:text-warning",
        danger:
          "border-destructive/40 border-l-destructive [&_svg]:text-destructive classic:border-destructive/30 classic:bg-destructive/10",
      },
    },
    defaultVariants: {
      variant: "info",
    },
  }
);

interface InlineAlertProps extends VariantProps<typeof inlineAlertVariants> {
  icon: React.ReactNode;
  description: React.ReactNode;
  className?: string;
}

function InlineAlert({
  variant,
  icon,
  description,
  className,
}: InlineAlertProps) {
  return (
    <div role="alert" className={cn(inlineAlertVariants({ variant }), className)}>
      <span className="shrink-0 [&>svg]:h-5 [&>svg]:w-5">{icon}</span>
      <div className="text-muted-foreground text-sm">{description}</div>
    </div>
  );
}

export { InlineAlert, inlineAlertVariants };
