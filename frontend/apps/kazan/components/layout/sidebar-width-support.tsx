import type { ReactNode } from "react";
import { NextIntlClientProvider } from "next-intl";
import { SidebarProvider, useSidebar } from "@oba/ui/sky/sidebar";
import en from "@/locales/en.json";
import { SidebarWidthProvider, useSidebarWidthContext } from "./sidebar-context";

// Support for the sidebar width tests. The stand-in DOM has no storage and no
// animation frames, so a test seeds both on the window before the width
// provider's effects run.
export const STORAGE_KEY = "kazan-sidebar-width";

export const storage = { store: new Map<string, string>(), unavailable: false };

/** Installs the storage on the window during render, ahead of every effect of its siblings. */
export function Seed() {
  const target = window as unknown as Record<string, unknown>;
  target.localStorage = storage.unavailable
    ? {
        getItem: () => {
          throw new Error("storage blocked");
        },
        setItem: () => {
          throw new Error("storage blocked");
        },
      }
    : {
        getItem: (key: string) => storage.store.get(key) ?? null,
        setItem: (key: string, value: string) => {
          storage.store.set(key, value);
        },
      };
  target.requestAnimationFrame = () => 0;
  target.cancelAnimationFrame = () => undefined;
  return null;
}

/** Shows the width and the style the provider hands to the shell, and the sidebar's open state. */
export function Probe() {
  const { width, providerStyle } = useSidebarWidthContext();
  const { open } = useSidebar();
  return (
    <div
      data-probe=""
      data-width={String(width)}
      data-css-width={String((providerStyle as Record<string, string>)["--sidebar-width"])}
      data-open={String(open)}
    />
  );
}

export function tree(children: ReactNode) {
  return (
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <Seed />
      <SidebarProvider>
        <SidebarWidthProvider>{children}</SidebarWidthProvider>
      </SidebarProvider>
    </NextIntlClientProvider>
  );
}
