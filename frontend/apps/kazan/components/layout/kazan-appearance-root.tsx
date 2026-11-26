"use client";

import { useEffect } from "react";
import { useKazanAppearance } from "@/features/settings/hooks/use-kazan-appearance";

/**
 * Keeps the layout and background attributes of <html> in step with the
 * user's saved appearance, so a change in the settings applies without a
 * reload. The server renders the same attributes for the first paint.
 * Renders nothing.
 */
export function KazanAppearanceRoot() {
  const { layout, background } = useKazanAppearance();

  useEffect(() => {
    const root = document.documentElement;
    root.dataset.kazanLayout = layout;
    root.dataset.kazanBackground = background;
  }, [layout, background]);

  return null;
}
