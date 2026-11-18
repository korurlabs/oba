import * as LabelPrimitive from "@rn-primitives/label";
import * as React from "react";

import { cn } from "@/lib/utils";

/**
 * A field label in `footnote` medium. Give it a `nativeID` and point the
 * field's `aria-labelledby` at it, so screen readers name the field.
 */
function Label({
  className,
  onPress,
  onLongPress,
  onPressIn,
  onPressOut,
  disabled,
  ...props
}: React.ComponentProps<typeof LabelPrimitive.Text>) {
  return (
    <LabelPrimitive.Root
      className={cn("flex-row items-center gap-2", disabled && "opacity-50")}
      onPress={onPress}
      onLongPress={onLongPress}
      onPressIn={onPressIn}
      onPressOut={onPressOut}
      disabled={disabled}
    >
      <LabelPrimitive.Text
        className={cn("font-sans-medium text-footnote text-foreground", className)}
        {...props}
      />
    </LabelPrimitive.Root>
  );
}

export { Label };
