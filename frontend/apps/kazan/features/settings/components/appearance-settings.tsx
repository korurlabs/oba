"use client";

import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { cn } from "@oba/ui/lib/utils";
import {
  KAZAN_BACKGROUNDS,
  KAZAN_LAYOUTS,
  type KazanBackground,
  type KazanLayout,
} from "@/lib/kazan-appearance";
import {
  useKazanAppearance,
  useUpdateKazanAppearance,
  useUpdateTheme,
} from "../hooks/use-kazan-appearance";

const THEMES = ["system", "light", "dark"] as const;
type ThemeChoice = (typeof THEMES)[number];

interface Option<T extends string> {
  value: T;
  label: string;
  hint?: string;
  preview: ReactNode;
}

interface OptionGroupProps<T extends string> {
  title: string;
  description: string;
  options: Option<T>[];
  /** Undefined until the current value is known, so no tile is marked. */
  value: T | undefined;
  disabled: boolean;
  onChange: (value: T) => void;
}

/**
 * One setting as a row of tiles in a radio group. The arrow keys move the
 * choice, as in a native radio group, and the tab key enters and leaves the
 * group at the chosen tile.
 */
function OptionGroup<T extends string>({
  title,
  description,
  options,
  value,
  disabled,
  onChange,
}: OptionGroupProps<T>) {
  const headingId = useId();
  const tiles = useRef<(HTMLButtonElement | null)[]>([]);
  const focusIndex = Math.max(
    0,
    options.findIndex((option) => option.value === value)
  );

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const step =
      event.key === "ArrowRight" || event.key === "ArrowDown"
        ? 1
        : event.key === "ArrowLeft" || event.key === "ArrowUp"
          ? -1
          : 0;
    if (step === 0) return;
    event.preventDefault();
    const next = (index + step + options.length) % options.length;
    const option = options[next];
    if (!option) return;
    tiles.current[next]?.focus();
    onChange(option.value);
  };

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3">
      <div>
        <h2 id={headingId} className="text-foreground text-lg font-semibold">
          {title}
        </h2>
        <p className="text-muted-foreground text-sm">{description}</p>
      </div>
      <div
        role="radiogroup"
        aria-labelledby={headingId}
        className="grid grid-cols-2 gap-3 md:grid-cols-4"
      >
        {options.map((option, index) => {
          const checked = option.value === value;
          return (
            <button
              key={option.value}
              ref={(node) => {
                tiles.current[index] = node;
              }}
              type="button"
              role="radio"
              aria-checked={checked}
              tabIndex={index === focusIndex ? 0 : -1}
              disabled={disabled}
              onClick={() => onChange(option.value)}
              onKeyDown={(event) => onKeyDown(event, index)}
              className={cn(
                "border-border bg-background flex cursor-pointer flex-col gap-2 rounded-md border p-2 text-left transition-colors",
                "hover:border-primary/60 disabled:cursor-wait disabled:opacity-70",
                checked && "border-primary ring-primary ring-1"
              )}
            >
              <span aria-hidden="true" className="block h-16 overflow-hidden rounded-sm border">
                {option.preview}
              </span>
              <span className="text-foreground text-sm font-medium">{option.label}</span>
              {option.hint && (
                <span className="text-muted-foreground text-xs leading-snug">{option.hint}</span>
              )}
            </button>
          );
        })}
      </div>
    </section>
  );
}

/** A small drawing of a layout: where the menu and the content sit. */
function LayoutPreview({ layout }: { layout: KazanLayout }) {
  if (layout === "classic") {
    return (
      <span className="bg-muted flex h-full p-1">
        <span className="flex w-1/4 flex-col gap-1 p-1">
          <span className="h-1.5 bg-muted-foreground/30 rounded-sm" />
          <span className="h-1 bg-muted-foreground/30 rounded-sm" />
          <span className="h-1 rounded-sm bg-[#2f8fe9]/50" />
          <span className="h-1 bg-muted-foreground/30 rounded-sm" />
        </span>
        <span className="bg-background flex-1 rounded-md border" />
      </span>
    );
  }
  return (
    <span className="flex h-full flex-col bg-[#f6fbff] dark:bg-[#0b1622]">
      <span className="h-2.5 border-b border-[#2f8fe9]/25 bg-white dark:bg-[#0f1d2b]" />
      <span className="flex flex-1 gap-1 [background-image:linear-gradient(rgb(47_143_233/0.12)_1px,transparent_1px),linear-gradient(90deg,rgb(47_143_233/0.12)_1px,transparent_1px)] [background-size:6px_6px] p-1">
        <span className="w-1/4 border border-[#2f8fe9]/40 bg-[#c4e2ff] dark:bg-[#14304c]" />
        <span className="flex-1" />
      </span>
    </span>
  );
}

/** A small drawing of a theme; System is half light and half dark. */
function ThemePreview({ theme }: { theme: ThemeChoice }) {
  const light = <span className="flex-1 bg-white" />;
  const dark = <span className="flex-1 bg-[#0b1622]" />;
  return (
    <span className="flex h-full">
      {theme === "dark" ? dark : light}
      {theme === "light" ? light : dark}
    </span>
  );
}

const BACKGROUND_PREVIEW: Record<KazanBackground, string> = {
  grid: "bg-[#f6fbff] dark:bg-[#0b1622] [background-image:linear-gradient(rgb(47_143_233/0.15)_1px,transparent_1px),linear-gradient(90deg,rgb(47_143_233/0.15)_1px,transparent_1px)] [background-size:8px_8px]",
  plain: "bg-background",
  mist: "bg-[#f6fbff] dark:bg-[#0f1a26]",
  slate: "bg-[#f5f5f5] dark:bg-[#121212]",
};

/**
 * Kazan's appearance settings: the layout, the theme and the background, each
 * saved to the user's preferences the moment it is chosen. The layout and the
 * background apply at once through the cached user; the theme is applied once
 * its save went through. While a save is pending the tiles are disabled, so
 * two saves never race over the preferences.
 */
export function AppearanceSettings() {
  const t = useTranslations("appearance");
  const { layout, background } = useKazanAppearance();
  const updateAppearance = useUpdateKazanAppearance();
  const updateTheme = useUpdateTheme();
  const { theme, setTheme } = useTheme();
  // The theme is known only in the browser, so no theme tile is marked
  // before the first render there.
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const pending = updateAppearance.isPending || updateTheme.isPending;
  const currentTheme =
    mounted && THEMES.includes(theme as ThemeChoice) ? (theme as ThemeChoice) : undefined;

  const changeLayout = (next: KazanLayout) => {
    if (next !== layout) updateAppearance.mutate({ layout: next });
  };
  const changeBackground = (next: KazanBackground) => {
    if (next !== background) updateAppearance.mutate({ background: next });
  };
  const changeTheme = (next: ThemeChoice) => {
    if (next === currentTheme) return;
    // The theme is applied only after the save succeeded.
    updateTheme.mutate(next, { onSuccess: () => setTheme(next) });
  };

  return (
    <div className="flex flex-col gap-10">
      <OptionGroup
        title={t("layout.title")}
        description={t("layout.description")}
        value={layout}
        disabled={pending}
        onChange={changeLayout}
        options={KAZAN_LAYOUTS.map((value) => ({
          value,
          label: t(`layout.${value}`),
          hint: t(`layout.${value}Hint`),
          preview: <LayoutPreview layout={value} />,
        }))}
      />
      <OptionGroup
        title={t("theme.title")}
        description={t("theme.description")}
        value={currentTheme}
        disabled={pending}
        onChange={changeTheme}
        options={THEMES.map((value) => ({
          value,
          label: t(`theme.${value}`),
          preview: <ThemePreview theme={value} />,
        }))}
      />
      <OptionGroup
        title={t("background.title")}
        description={t("background.description")}
        value={background}
        disabled={pending}
        onChange={changeBackground}
        options={KAZAN_BACKGROUNDS.map((value) => ({
          value,
          label: t(`background.${value}`),
          preview: <span className={cn("block h-full", BACKGROUND_PREVIEW[value])} />,
        }))}
      />
    </div>
  );
}
