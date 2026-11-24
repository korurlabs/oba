import {
  NextIntlClientProvider,
  OrganizationSyncProvider,
  QueryClientProvider,
  QueryParamsProvider,
  RuntimeConfigProvider,
  SessionProvider,
  ThemeProvider,
} from "@oba/shared/providers";
import { Toaster } from "@oba/ui/components/sonner";
import React from "react";
import { Locale } from "@oba/shared/types";
import type { RuntimeConfig } from "@oba/shared/lib/runtime-config";
import { ShortcutListener } from "./shortcut-listener";

export function Providers({
  children,
  locale,
  runtimeConfig,
}: {
  children: React.ReactNode;
  locale: Locale;
  runtimeConfig: RuntimeConfig;
}) {
  return (
    <RuntimeConfigProvider config={runtimeConfig}>
      <NextIntlClientProvider locale={locale}>
        <QueryParamsProvider>
          {/* The sky design has no dark mode, so a stored dark preference is not applied. */}
          <ThemeProvider forcedTheme="light">
            <SessionProvider>
              <QueryClientProvider>
                <OrganizationSyncProvider>
                  <ShortcutListener />
                  {children}
                  <Toaster closeButton />
                </OrganizationSyncProvider>
              </QueryClientProvider>
            </SessionProvider>
          </ThemeProvider>
        </QueryParamsProvider>
      </NextIntlClientProvider>
    </RuntimeConfigProvider>
  );
}
