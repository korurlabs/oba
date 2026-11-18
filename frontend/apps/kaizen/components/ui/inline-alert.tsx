import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const inlineAlertVariants = cva(
  "flex flex-row items-center gap-2 rounded-lg border p-3",
  {
    variants: {
      variant: {
        info: "border-info/30 bg-info/10 [&>svg]:text-info",
        success: "border-success/30 bg-success/10 [&>svg]:text-success",
        warning: "border-warning/30 bg-warning/10 [&>svg]:text-warning",
        danger:
          "border-destructive/30 bg-destructive/10 [&>svg]:text-destructive",
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
