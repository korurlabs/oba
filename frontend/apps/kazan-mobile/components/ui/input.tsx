import * as React from "react";
import type { TextInput } from "react-native";

import { SheetTextInput } from "@/components/ui/sheet-text-input";
import { cn } from "@/lib/utils";

type InputProps = React.ComponentProps<typeof TextInput> & {
  /** Draws the `destructive` outline of a field with an error. */
  invalid?: boolean;
};

/**
 * A single-line field: `background` ground, `input` outline that turns `ring`
 * on focus, radius-md, 44 pt tall. The size is 17 without a line height,
 * because a line height shifts single-line text off centre on iOS.
 */
function Input({ className, invalid = false, editable, onFocus, onBlur, ...props }: InputProps) {
  const [focused, setFocused] = React.useState(false);
  return (
    <SheetTextInput
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
        "min-h-11 w-full rounded-md border bg-background px-3 font-sans text-[17px] text-foreground placeholder:text-muted-foreground",
        invalid ? "border-destructive" : focused ? "border-ring" : "border-input",
        editable === false && "opacity-50",
        className
      )}
      {...props}
    />
  );
}

export { Input };
export type { InputProps };
