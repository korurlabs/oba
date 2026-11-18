import { Slot } from "@rn-primitives/slot";
import { cva, type VariantProps } from "class-variance-authority";
import * as React from "react";
import { Text as RNText, type Role } from "react-native";

import { cn } from "@/lib/utils";

/*
 * The Kaizen type scale, named after iOS Dynamic Type; Android uses the same
 * sizes. The weight is the font family (font-sans-semibold), because the
 * bundled Inter faces are static and ignore fontWeight. Font scaling stays on,
 * so every style follows the system text size.
 */
const textVariants = cva("", {
  variants: {
    variant: {
      "large-title": "font-sans-bold text-large-title",
      "title-1": "font-sans-bold text-title-1",
      "title-2": "font-sans-bold text-title-2",
      "title-3": "font-sans-semibold text-title-3",
      headline: "font-sans-semibold text-headline",
      body: "font-sans text-body",
      callout: "font-sans text-callout",
      subhead: "font-sans-medium text-subhead",
      footnote: "font-sans text-footnote",
      caption: "font-sans-medium text-caption",
      "caption-2": "font-sans-medium text-caption-2",
      button: "font-sans-semibold text-button",
      meta: "font-sans-medium text-meta",
      badge: "font-sans-semibold text-badge uppercase",
      "ticket-key": "font-mono-medium text-ticket-key text-muted-foreground",
      code: "font-mono text-code",
    },
  },
});

type TextVariantProps = VariantProps<typeof textVariants>;

type TextVariant = NonNullable<TextVariantProps["variant"]>;

const HEADING_LEVEL: Partial<Record<TextVariant, number>> = {
  "large-title": 1,
  "title-1": 1,
  "title-2": 2,
  "title-3": 3,
};

/**
 * Text classes a container (Button, Badge, Card) pushes down to every `Text`
 * inside it, so a label takes the container's colour and size without props.
 */
const TextClassContext = React.createContext<string | undefined>(undefined);

type TextProps = React.ComponentProps<typeof RNText> &
  React.RefAttributes<RNText> &
  TextVariantProps & {
    asChild?: boolean;
  };

function Text({ className, asChild = false, variant, ...props }: TextProps) {
  const textClass = React.useContext(TextClassContext);
  const Component = asChild ? Slot : RNText;
  const level = variant ? HEADING_LEVEL[variant] : undefined;
  return (
    <Component
      // A container's classes come first: a variant then sets only size and
      // weight, so <Text variant="footnote"> inside a Button keeps the button's
      // colour. Without a container, text is `body` in `foreground`.
      className={cn(
        "font-sans",
        textClass ?? "text-body text-foreground",
        variant ? textVariants({ variant }) : undefined,
        className
      )}
      role={level ? ("heading" as Role) : undefined}
      aria-level={level}
      {...props}
    />
  );
}

export { Text, TextClassContext, textVariants };
export type { TextProps, TextVariant };
