"use client";

import { useId, useState, type ComponentProps, type CSSProperties, type ReactNode } from "react";
import { Check, Pipette, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { HexColorInput, HexColorPicker } from "react-colorful";
import { Popover, PopoverContent, PopoverTrigger } from "@oba/ui/components/popover";
import { cn } from "@oba/ui/lib/utils";
import { getReadableTextColor, isSameColor, normalizeHexColor } from "@/lib/utils/color";

export interface ColorOption {
  name: string;
  value: string;
}

interface ColorPickerProps {
  /** Accessible name of the group; shown above the swatches unless `hideLabel`. */
  label: string;
  value: string | null | undefined;
  /** Receives every chosen colour as uppercase `#RRGGBB`. */
  onChange: (value: string) => void;
  /** The palette offered as swatches; each feature keeps its own. */
  options: readonly ColorOption[];
  disabled?: boolean;
  /** `tile`: large named squares (dialogs). `dot`: small circles (inline forms). */
  variant?: "tile" | "dot";
  hideLabel?: boolean;
}

const RAINBOW: CSSProperties = {
  background:
    "conic-gradient(from 90deg, #EF4444, #F59E0B, #EAB308, #22C55E, #06B6D4, #3B82F6, #8B5CF6, #EC4899, #EF4444)",
};

/**
 * The single colour selector of the app: a palette of swatches followed by a
 * "Custom" swatch that opens a free hex picker. A value outside the palette
 * is shown as the selected Custom swatch, so a colour chosen earlier, or set
 * before the palette changed, stays visible and is never silently replaced.
 */
export function ColorPicker({
  label,
  value,
  onChange,
  options,
  disabled,
  variant = "tile",
  hideLabel,
}: ColorPickerProps) {
  const t = useTranslations("common.colorPicker");
  const hexInputId = useId();
  const [open, setOpen] = useState(false);

  const current = normalizeHexColor(value);
  const inPalette = !!current && options.some((option) => isSameColor(option.value, current));
  // The last custom colour stays on its swatch after a palette colour is picked,
  // so the user can switch back to it with one click.
  const [lastCustom, setLastCustom] = useState<string | null>(inPalette ? null : current);
  const customSelected = !!current && !inPalette;
  const customColor = customSelected ? current : lastCustom;

  const applyCustom = (raw: string) => {
    const hex = normalizeHexColor(raw);
    if (!hex) return;
    setLastCustom(hex);
    onChange(hex);
  };

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (next && customColor && !customSelected) onChange(customColor);
  };

  const isTile = variant === "tile";
  const swatchClass = cn(
    "relative flex shrink-0 items-center justify-center transition-all",
    isTile ? "h-12 w-12 rounded-lg" : "h-6 w-6 rounded-full",
  );
  const selectedClass = "ring-primary ring-offset-background scale-110 ring-2 ring-offset-2";
  const idleClass = isTile ? "hover:scale-105" : "hover:scale-110";

  const renderSwatch = (
    key: string,
    name: string,
    selected: boolean,
    swatch: ReactNode,
    extra?: Omit<ComponentProps<"button">, "children">,
  ) => (
    <button
      key={key}
      type="button"
      role="radio"
      aria-checked={selected}
      aria-label={name}
      title={name}
      disabled={disabled}
      className={cn(
        "group focus-visible:ring-ring relative flex flex-col items-center gap-2 rounded-lg outline-none focus-visible:ring-2",
        "disabled:cursor-not-allowed disabled:opacity-50",
      )}
      {...extra}
    >
      {swatch}
      {isTile && <span className="text-muted-foreground text-xs">{name}</span>}
      {isTile && selected && (
        <span className="bg-primary text-primary-foreground absolute -top-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full">
          <Check className="h-3 w-3" strokeWidth={3} aria-hidden />
        </span>
      )}
    </button>
  );

  const customName = customColor ? t("customColor", { color: customColor }) : t("custom");

  return (
    <div className="flex flex-col items-start gap-2" role="radiogroup" aria-label={label}>
      {!hideLabel && <span className="text-sm font-medium">{label}</span>}
      <div className={cn("flex flex-wrap self-stretch p-1", isTile ? "gap-3" : "gap-2")}>
        {options.map((option) => {
          const selected = isSameColor(current, option.value);
          return renderSwatch(
            option.value,
            option.name,
            selected,
            <span
              className={cn(swatchClass, selected ? selectedClass : idleClass)}
              style={{ backgroundColor: option.value }}
              aria-hidden
            />,
            { onClick: () => onChange(normalizeHexColor(option.value) ?? option.value) },
          );
        })}

        <Popover open={open} onOpenChange={handleOpenChange}>
          <PopoverTrigger asChild>
            {renderSwatch(
              "custom",
              isTile ? t("custom") : customName,
              customSelected,
              <span
                className={cn(swatchClass, customSelected ? selectedClass : idleClass)}
                style={customColor ? { backgroundColor: customColor } : RAINBOW}
                aria-hidden
              >
                {customColor ? (
                  <Pipette
                    className={isTile ? "h-4 w-4" : "h-3 w-3"}
                    style={{ color: getReadableTextColor(customColor) }}
                  />
                ) : (
                  <span className="bg-background text-foreground flex items-center justify-center rounded-full p-0.5 shadow-sm">
                    <Plus className={isTile ? "h-3.5 w-3.5" : "h-2.5 w-2.5"} strokeWidth={3} />
                  </span>
                )}
              </span>,
              isTile ? { "aria-label": customName } : undefined,
            )}
          </PopoverTrigger>
          <PopoverContent className="w-auto space-y-3 p-3" align="start">
            <HexColorPicker color={current ?? customColor ?? options[0]?.value} onChange={applyCustom} />
            <div className="space-y-1.5">
              <label htmlFor={hexInputId} className="text-muted-foreground text-xs font-medium">
                {t("hex")}
              </label>
              <HexColorInput
                id={hexInputId}
                color={current ?? customColor ?? ""}
                onChange={applyCustom}
                prefixed
                placeholder="#RRGGBB"
                className={cn(
                  "border-input bg-background text-foreground dark:bg-input/30 flex h-9 w-[200px] rounded-md border px-3 font-mono text-sm uppercase",
                  "focus:border-primary focus:ring-primary/20 focus:ring-[3px] focus:outline-none",
                )}
              />
            </div>
          </PopoverContent>
        </Popover>
      </div>
    </div>
  );
}
