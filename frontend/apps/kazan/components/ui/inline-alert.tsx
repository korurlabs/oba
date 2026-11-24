import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

/*
 * A notice in the grid look: a card-coloured plate with a hairline rim and a
 * 2px leading rule. The rule and the icon carry the tone (primary for
 * information, ink for a warning, red for danger), never a tinted fill.
 */
const inlineAlertVariants = cva(
  "flex flex-row items-center gap-2 border border-l-2 bg-card p-3",
  {
    variants: {
      variant: {
        info: "border-primary/30 border-l-primary [&_svg]:text-primary",
        success: "border-primary/30 border-l-korur-blue-strong [&_svg]:text-korur-blue-strong",
        warning: "border-foreground/25 border-l-foreground/70 [&_svg]:text-foreground",
        danger: "border-destructive/40 border-l-destructive [&_svg]:text-destructive",
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
