import * as RadioGroupPrimitive from "@rn-primitives/radio-group";
import * as React from "react";
import { View } from "react-native";

import { cn } from "@/lib/utils";

type RadioMarkProps = {
  checked: boolean;
  disabled?: boolean;
  className?: string;
};

/**
 * The radio circle alone, for a row that is itself the touch target (a List
 * row with the `radio` accessory). The row carries `accessibilityRole="radio"`
 * and the checked state.
 */
function RadioMark({ checked, disabled = false, className }: RadioMarkProps) {
  return (
    <View
      importantForAccessibility="no-hide-descendants"
      accessibilityElementsHidden
      className={cn(
        "size-5 items-center justify-center rounded-full border-2",
        checked ? "border-primary-fill" : "border-muted-foreground",
        disabled && "opacity-50",
        className
      )}
    >
      {checked ? <View className="size-2.5 rounded-full bg-primary-fill" /> : null}
    </View>
  );
}

/** A group of choices of which exactly one is picked; `value` and `onValueChange`. */
function RadioGroup({
  className,
  ...props
}: React.ComponentProps<typeof RadioGroupPrimitive.Root>) {
  return <RadioGroupPrimitive.Root className={cn("gap-3", className)} {...props} />;
}

/**
 * One choice of a RadioGroup. Name it with `aria-label`, or put it next to a
 * Label whose `onPress` selects it.
 */
function RadioGroupItem({
  className,
  disabled,
  ...props
}: Omit<React.ComponentProps<typeof RadioGroupPrimitive.Item>, "children">) {
  return (
    <RadioGroupPrimitive.Item
      disabled={disabled}
      hitSlop={14}
      className={cn(
        "size-5 items-center justify-center rounded-full border-2 border-muted-foreground",
        disabled && "opacity-50",
        className
      )}
      {...props}
    >
      {/* Drawn over the item's own border, so the picked ring turns blue. */}
      <RadioGroupPrimitive.Indicator className="absolute -left-0.5 -top-0.5 size-5 items-center justify-center rounded-full border-2 border-primary-fill">
        <View className="size-2.5 rounded-full bg-primary-fill" />
      </RadioGroupPrimitive.Indicator>
    </RadioGroupPrimitive.Item>
  );
}

export { RadioGroup, RadioGroupItem, RadioMark };
export type { RadioMarkProps };
