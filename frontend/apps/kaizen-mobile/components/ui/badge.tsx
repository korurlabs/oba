import { cva, type VariantProps } from "class-variance-authority";
import * as React from "react";
import { View } from "react-native";

import { Text, TextClassContext } from "@/components/ui/text";
import { cn } from "@/lib/utils";

/*
 * The metadata shapes. Ticket metadata has one scale, so a piece is never
 * resized alone: `pill` is the 24 pt `meta` pill (status, health, label),
 * `tag` the 20 pt uppercase `badge` tag (ticket type), `tag-sm` its 18 pt form
 * for dense rows, `outline` the outlined mono tag (mission rank) and `count`
 * a notification count. The colours come from the caller (`className` for the
 * ground, `textClassName` for the label), because each set has its own tokens.
 * The heights are minimums, so a shape grows with the system text size
 * instead of clipping its label.
 */
const badgeVariants = cva("shrink-0 flex-row items-center self-start", {
  variants: {
    variant: {
      pill: "min-h-6 gap-1.5 rounded-full bg-muted px-2.5",
      tag: "min-h-5 rounded-xs px-1.5",
      "tag-sm": "min-h-[18px] rounded-xs px-1",
      outline: "min-h-5 rounded-xs border border-border px-1.5",
      count: "min-h-5 min-w-5 justify-center rounded-full bg-destructive-fill px-1.5",
    },
  },
  defaultVariants: { variant: "pill" },
});

const badgeTextVariants = cva("", {
  variants: {
    variant: {
      pill: "font-sans-medium text-meta text-foreground",
      tag: "font-sans-semibold text-badge uppercase",
      "tag-sm": "font-sans-semibold text-[10px] uppercase leading-3 tracking-[0.4px]",
      outline: "font-mono-medium text-badge text-muted-foreground",
      count: "font-sans-semibold text-caption text-destructive-foreground",
    },
  },
  defaultVariants: { variant: "pill" },
});

type BadgeVariant = NonNullable<VariantProps<typeof badgeVariants>["variant"]>;

type BadgeProps = React.ComponentProps<typeof View> & {
  variant?: BadgeVariant;
  /** Extra classes for every `Text` and `Icon` inside, such as the label colour. */
  textClassName?: string;
};

/** A string or number child becomes the label; other children render as they are. */
function Badge({ className, textClassName, variant = "pill", children, ...props }: BadgeProps) {
  const content =
    typeof children === "string" || typeof children === "number" ? (
      <Text numberOfLines={1}>{children}</Text>
    ) : (
      children
    );
  return (
    <TextClassContext.Provider value={cn(badgeTextVariants({ variant }), textClassName)}>
      <View className={cn(badgeVariants({ variant }), className)} {...props}>
        {content}
      </View>
    </TextClassContext.Provider>
  );
}

export { Badge, badgeTextVariants, badgeVariants };
export type { BadgeProps, BadgeVariant };
