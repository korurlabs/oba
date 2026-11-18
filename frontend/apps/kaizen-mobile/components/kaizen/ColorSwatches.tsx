import * as React from "react";
import { Platform, Pressable, View } from "react-native";

import { useThemeColors } from "@/components/ui/use-theme-colors";
import type { PaletteColor } from "@/lib/schemas/boards";
import { cn } from "@/lib/utils";

/*
 * A colour choice from a fixed palette, such as a label's or a milestone's
 * colour: wrapping 32 pt circles, the chosen one inside a `primary` ring. Each
 * circle sits in a 44 pt (iOS) or 48 dp (Android) touch target and is a radio
 * with the colour's name, so the choice does not rest on colour alone.
 */

const HIT_SIZE: number = Platform.OS === "ios" ? 44 : 48;

type ColorSwatchesProps = {
  colors: readonly PaletteColor[];
  /** The chosen colour; matched without regard to case. None is ringed when it is not in the palette. */
  value: string | null;
  onChange: (color: string) => void;
  /** The spoken name of a colour, such as a translated "Blue". */
  labelOf: (color: PaletteColor) => string;
  /** The spoken name of the whole choice, such as "Colour". */
  accessibilityLabel?: string;
  disabled?: boolean;
  className?: string;
};

function sameColor(a: string | null, b: string): boolean {
  return !!a && a.toLowerCase() === b.toLowerCase();
}

function ColorSwatches({
  colors,
  value,
  onChange,
  labelOf,
  accessibilityLabel,
  disabled = false,
  className,
}: ColorSwatchesProps) {
  const themeColors = useThemeColors();
  return (
    <View
      accessibilityRole="radiogroup"
      accessibilityLabel={accessibilityLabel}
      className={cn("flex-row flex-wrap", className)}
    >
      {colors.map((color) => {
        const selected: boolean = sameColor(value, color.value);
        return (
          <Pressable
            key={color.value}
            accessibilityRole="radio"
            accessibilityLabel={labelOf(color)}
            accessibilityState={{ checked: selected, disabled }}
            disabled={disabled}
            onPress={() => {
              if (!selected) onChange(color.value);
            }}
            android_ripple={{ color: themeColors.accent, borderless: true, radius: HIT_SIZE / 2 }}
            className={cn(
              "items-center justify-center",
              disabled && "opacity-50",
              Platform.select({ ios: "active:opacity-60" })
            )}
            style={{ width: HIT_SIZE, height: HIT_SIZE }}
          >
            <View
              className={cn(
                "size-[40px] items-center justify-center rounded-full border-2",
                selected ? "border-primary" : "border-transparent"
              )}
            >
              <View className="size-8 rounded-full" style={{ backgroundColor: color.value }} />
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}

export { ColorSwatches };
export type { ColorSwatchesProps };
