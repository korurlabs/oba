/**
 * Switch Component
 * A toggle switch component for binary states
 */

"use client";

import * as React from "react";
import * as SwitchPrimitives from "@radix-ui/react-switch";
import { cn } from "@/lib/utils";

const Switch = React.forwardRef<
  React.ElementRef<typeof SwitchPrimitives.Root>,
  React.ComponentPropsWithoutRef<typeof SwitchPrimitives.Root>
>(({ className, ...props }, ref) => (
  <SwitchPrimitives.Root
    data-slot="switch"
    className={cn(
      // A square track with a hairline rim: off is an empty frame, on is filled primary.
      "peer focus-visible:ring-ring focus-visible:ring-offset-background border-foreground/25 data-[state=unchecked]:bg-background data-[state=checked]:border-primary data-[state=checked]:bg-primary classic:rounded-full classic:border-2 classic:border-transparent classic:shadow-sm classic:data-[state=unchecked]:bg-input classic:data-[state=checked]:border-transparent classic:data-[state=checked]:bg-primary/50 classic:dark:data-[state=checked]:bg-primary inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-none border transition-colors focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50",
      className
    )}
    {...props}
    ref={ref}
  >
    <SwitchPrimitives.Thumb
      data-slot="switch-thumb"
      className={cn(
        "bg-foreground/45 data-[state=checked]:bg-primary-foreground classic:size-4 classic:rounded-full classic:bg-primary classic:shadow-lg classic:dark:bg-foreground classic:data-[state=checked]:bg-primary classic:data-[state=checked]:translate-x-4 classic:data-[state=unchecked]:-translate-x-1 pointer-events-none block size-3.5 rounded-none ring-0 transition-transform data-[state=checked]:translate-x-[18px] data-[state=unchecked]:translate-x-0.5"
      )}
    />
  </SwitchPrimitives.Root>
));
Switch.displayName = SwitchPrimitives.Root.displayName;

export { Switch };
