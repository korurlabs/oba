"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SessionProvider } from "next-auth/react";
import { NuqsAdapter } from "nuqs/adapters/next/app";
import { useState, type ReactNode } from "react";

import type { RuntimeConfig } from "@oba/shared/lib/runtime-config";
import { RuntimeConfigProvider } from "@oba/shared/providers/RuntimeConfigProvider";

import { reportAccessLoss } from "@/lib/api/access";

/** One minute: the stream of a later release refreshes sessions sooner; a reload always does. */
const STALE_TIME_MS = 60_000;

function makeQueryClient(): QueryClient {
  return new QueryClient({
    // A 401 or a lost membership can surface on any call; the identity gate
    // turns it into the sign-in or "not a member" page. Every other error is
    // shown by the component that made the call.
    queryCache: new QueryCache({ onError: reportAccessLoss }),
    mutationCache: new MutationCache({ onError: reportAccessLoss }),
    defaultOptions: {
      queries: {
        staleTime: STALE_TIME_MS,
        retry: false,
        refetchOnWindowFocus: false,
      },
    },
  });
}

export function Providers({
  children,
  runtimeConfig,
}: {
  children: ReactNode;
  runtimeConfig: RuntimeConfig;
}) {
  const [queryClient] = useState(makeQueryClient);
  return (
    <RuntimeConfigProvider config={runtimeConfig}>
      <SessionProvider>
        <QueryClientProvider client={queryClient}>
          <NuqsAdapter>{children}</NuqsAdapter>
        </QueryClientProvider>
      </SessionProvider>
    </RuntimeConfigProvider>
  );
}
