"use client";

import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { Monitor, Moon, Sun } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { useEffect, useState } from "react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { Card, CardContent, CardDescription, CardHeader } from "@oba/ui/sky/card";
import { PanelTitle } from "@/components/layout/page-heading";
import { useUpdateTheme } from "../hooks/use-update-theme";

const THEME_OPTIONS = [
  { value: "light", icon: Sun, label: "settings.themeLight" },
  { value: "dark", icon: Moon, label: "settings.themeDark" },
  { value: "system", icon: Monitor, label: "settings.themeSystem" },
] as const;

export function AppearanceCard() {
  const t = useTranslations();
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  const { data: me } = useGetMe();
  const updateTheme = useUpdateTheme();
  const userTheme = me?.data?.userPreferences?.theme;

  useEffect(() => {
    setMounted(true);
  }, []);

  const handleThemeChange = (theme: string) => {
    if (userTheme === theme) return;
    // The theme is applied only after the save succeeded.
    updateTheme.mutate(theme, { onSuccess: () => setTheme(theme) });
  };

  return (
    <Card role="region" aria-labelledby="settings-appearance-heading">
      <CardHeader>
        <PanelTitle id="settings-appearance-heading">{t("settings.appearance")}</PanelTitle>
        <CardDescription>{t("settings.appearanceSubtitle")}</CardDescription>
      </CardHeader>
      <CardContent>
        {/* A segmented choice: the chosen theme carries the primary frame and
            the light accent fill, the others stay plain. */}
        <div className="grid grid-cols-3 gap-3">
          {THEME_OPTIONS.map(({ value, icon: Icon, label }) => {
            const isSelected = mounted && theme === value;
            return (
              <button
                key={value}
                type="button"
                aria-pressed={isSelected}
                onClick={() => handleThemeChange(value)}
                className={cn(
                  "flex cursor-pointer flex-col items-center gap-2 rounded-md border px-4 py-3 text-sm font-medium transition-colors",
                  isSelected
                    ? "border-primary bg-accent text-accent-foreground"
                    : "bg-background text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                )}
              >
                <Icon className="size-5" aria-hidden="true" />
                {t(label)}
              </button>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}
