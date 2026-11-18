import * as SeparatorPrimitive from "@rn-primitives/separator";
import * as React from "react";
import { StyleSheet } from "react-native";

import { cn } from "@/lib/utils";

/** A hairline in `border`, the iOS and Material divider weight. */
function Separator({
  className,
  orientation = "horizontal",
  decorative = true,
  style,
  ...props
}: React.ComponentProps<typeof SeparatorPrimitive.Root>) {
  return (
    <SeparatorPrimitive.Root
      decorative={decorative}
      orientation={orientation}
      className={cn(
        "shrink-0 bg-border",
        orientation === "horizontal" ? "w-full" : "h-full",
        className
      )}
      style={[
        orientation === "horizontal"
          ? { height: StyleSheet.hairlineWidth }
          : { width: StyleSheet.hairlineWidth },
        style,
      ]}
      {...props}
    />
  );
}

export { Separator };
