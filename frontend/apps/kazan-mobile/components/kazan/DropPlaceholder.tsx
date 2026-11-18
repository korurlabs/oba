import * as React from "react";
import { View } from "react-native";

import { cn } from "@/lib/utils";

type DropPlaceholderProps = {
  /** The height of the card that will land here. */
  height: number;
  className?: string;
};

/**
 * The slot a dragged ticket lands in: a dashed primary outline the height of
 * the card, so the cards below make room before the drop.
 */
function DropPlaceholder({ height, className }: DropPlaceholderProps) {
  return (
    <View
      importantForAccessibility="no-hide-descendants"
      accessibilityElementsHidden
      style={{ height }}
      className={cn(
        "rounded-lg border-2 border-dashed border-primary-medium bg-primary-light",
        className
      )}
    />
  );
}

export { DropPlaceholder };
export type { DropPlaceholderProps };
