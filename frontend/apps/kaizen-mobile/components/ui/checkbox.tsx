import * as CheckboxPrimitive from "@rn-primitives/checkbox";
import { Check } from "lucide-react-native";
import * as React from "react";
import { Platform, View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { cn } from "@/lib/utils";

/*
 * iOS has no system checkbox; multi-select in lists is the round mark of Mail
 * and Reminders. Android draws the Material square. Both fill with
 * primary-fill and a check in primary-foreground when checked.
 */
const MARK_SHAPE = Platform.select({
  ios: "size-[22px] rounded-full border-[1.5px]",
  default: "size-[18px] rounded-xs border-2",
});

type CheckboxMarkProps = {
  checked: boolean;
  disabled?: boolean;
  className?: string;
};

/**
 * The visual mark alone, for a row that is itself the touch target (a List
 * row with the `checkbox` accessory, a stale ticket row). It has no role of its
 * own; the row carries `accessibilityRole="checkbox"` and the checked state.
 */
function CheckboxMark({ checked, disabled = false, className }: CheckboxMarkProps) {
  return (
    <View
      importantForAccessibility="no-hide-descendants"
      accessibilityElementsHidden
      className={cn(
        "items-center justify-center",
        MARK_SHAPE,
        checked ? "border-primary-fill bg-primary-fill" : "border-muted-foreground bg-transparent",
        disabled && "opacity-50",
        className
      )}
    >
      {checked ? (
        <Icon as={Check} size={14} strokeWidth={3} className="text-primary-foreground" />
      ) : null}
    </View>
  );
}

type CheckboxProps = Omit<React.ComponentProps<typeof CheckboxPrimitive.Root>, "children"> & {
  className?: string;
};

/**
 * A standalone checkbox with its own touch target (44 or 48 pt through the hit
 * slop). Give it an `aria-label` or `accessibilityLabel` when no text names it.
 */
function Checkbox({ checked, disabled, className, ...props }: CheckboxProps) {
  return (
    <CheckboxPrimitive.Root
      checked={checked}
      disabled={disabled}
      hitSlop={Platform.select({ ios: 11, default: 15 })}
      className={cn("items-center justify-center", className)}
      {...props}
    >
      <CheckboxMark checked={checked} disabled={disabled ?? undefined} />
    </CheckboxPrimitive.Root>
  );
}

export { Checkbox, CheckboxMark };
export type { CheckboxMarkProps, CheckboxProps };
