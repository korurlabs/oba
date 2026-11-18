import { TriangleAlert } from "lucide-react-native";
import * as React from "react";
import { TextInput, View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Text } from "@/components/ui/text";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

type TextFieldProps = React.ComponentProps<typeof TextInput> & {
  /** Always visible above the field; also its accessible name. */
  label: string;
  /** A field the form cannot be sent without: a `destructive` asterisk after the label. */
  required?: boolean;
  /** The text input, such as to focus the field when it is missing. */
  ref?: React.Ref<TextInput>;
  /** Guidance under the field; hidden while an error shows. */
  helper?: string;
  /** The error message; draws the `destructive` outline and an icon. */
  error?: string | null;
  /** Fixed text inside the field on the right, such as a unit. */
  suffix?: string;
  multiline?: boolean;
  /** Visible lines of a multi-line field. */
  rows?: number;
  className?: string;
};

/** A labelled text field with helper or error text, single- or multi-line. */
function TextField({
  label,
  required = false,
  helper,
  error,
  suffix,
  multiline = false,
  rows,
  className,
  ...inputProps
}: TextFieldProps) {
  const labelId = React.useId();
  const messageId = React.useId();
  const invalid = !!error;
  const message = error || helper;

  return (
    <View className={cn("gap-1.5", className)}>
      <Label nativeID={labelId}>
        {label}
        {required ? <Text className="text-destructive"> *</Text> : null}
      </Label>
      <View className="justify-center">
        {multiline ? (
          <Textarea
            aria-labelledby={labelId}
            accessibilityLabel={label}
            accessibilityHint={message ?? undefined}
            invalid={invalid}
            rows={rows}
            {...inputProps}
          />
        ) : (
          <Input
            aria-labelledby={labelId}
            accessibilityLabel={label}
            accessibilityHint={message ?? undefined}
            invalid={invalid}
            className={suffix ? "pr-12" : undefined}
            {...inputProps}
          />
        )}
        {suffix && !multiline ? (
          <Text
            variant="body"
            className="absolute right-3 text-muted-foreground"
            importantForAccessibility="no"
          >
            {suffix}
          </Text>
        ) : null}
      </View>
      {error ? (
        <View nativeID={messageId} className="flex-row items-start gap-1.5">
          <Icon as={TriangleAlert} size={14} className="mt-0.5 text-destructive" />
          <Text variant="footnote" className="flex-1 text-destructive">
            {error}
          </Text>
        </View>
      ) : helper ? (
        <Text nativeID={messageId} variant="footnote" className="text-muted-foreground">
          {helper}
        </Text>
      ) : null}
    </View>
  );
}

export { TextField };
export type { TextFieldProps };
