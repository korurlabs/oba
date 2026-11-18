import * as React from "react";
import type { TextInput } from "react-native";

import { SheetTextInput } from "@/components/ui/sheet-text-input";
import { cn } from "@/lib/utils";

type TextareaProps = React.ComponentProps<typeof TextInput> & {
  /** Draws the `destructive` outline of a field with an error. */
  invalid?: boolean;
  /** Visible lines before the field scrolls; default 3. */
  rows?: number;
};

/** The multi-line form of Input: same ground, outline and focus ring. */
function Textarea({
  className,
  invalid = false,
  rows = 3,
  editable,
  onFocus,
  onBlur,
  ...props
}: TextareaProps) {
  const [focused, setFocused] = React.useState(false);
  return (
    <SheetTextInput
      multiline
      textAlignVertical="top"
      editable={editable}
      onFocus={(event) => {
        setFocused(true);
        onFocus?.(event);
      }}
      onBlur={(event) => {
        setFocused(false);
        onBlur?.(event);
      }}
      className={cn(
        "w-full rounded-md border bg-background px-3 py-2.5 font-sans text-body text-foreground placeholder:text-muted-foreground",
        invalid ? "border-destructive" : focused ? "border-ring" : "border-input",
        editable === false && "opacity-50",
        className
      )}
      // 22 pt per body line plus the vertical padding.
      style={{ minHeight: rows * 22 + 20 }}
      {...props}
    />
  );
}

export { Textarea };
export type { TextareaProps };
