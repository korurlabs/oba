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
          {/* The sky design has no dark mode, so Admin stays on the light theme. */}
          <ThemeProvider forcedTheme="light">
            <SessionProvider>
              <QueryClientProvider>
                <OrganizationSyncProvider>
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
