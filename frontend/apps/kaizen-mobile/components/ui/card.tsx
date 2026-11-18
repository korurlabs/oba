import * as React from "react";
import { View } from "react-native";

import { Text, TextClassContext } from "@/components/ui/text";
import { useShadow } from "@/components/ui/use-shadow";
import { cn } from "@/lib/utils";

type ViewProps = React.ComponentProps<typeof View>;

/**
 * A `card` surface on `screen`: radius-lg, 12 pt inside, the two-layer card
 * shadow in light mode. Never put a card on a card; nest with `bg-muted`.
 * `elevated` swaps the shadow for the lifted one of a dragged card.
 */
function Card({
  className,
  style,
  elevated = false,
  ...props
}: ViewProps & { elevated?: boolean }) {
  const shadow = useShadow(elevated ? "card-hover" : "card");
  return (
    <TextClassContext.Provider value="text-body text-card-foreground">
      <View
        className={cn("gap-2 rounded-lg bg-card p-3", className)}
        style={[shadow, style]}
        {...props}
      />
    </TextClassContext.Provider>
  );
}

function CardHeader({ className, ...props }: ViewProps) {
  return <View className={cn("flex-row items-center gap-2", className)} {...props} />;
}

function CardTitle({ className, ...props }: React.ComponentProps<typeof Text>) {
  return <Text variant="headline" className={cn("flex-1", className)} {...props} />;
}

function CardDescription({ className, ...props }: React.ComponentProps<typeof Text>) {
  return <Text variant="footnote" className={cn("text-muted-foreground", className)} {...props} />;
}

function CardContent({ className, ...props }: ViewProps) {
  return <View className={cn("gap-2", className)} {...props} />;
}

function CardFooter({ className, ...props }: ViewProps) {
  return <View className={cn("flex-row items-center gap-2", className)} {...props} />;
}

export { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle };
