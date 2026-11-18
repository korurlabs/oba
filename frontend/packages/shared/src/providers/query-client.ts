import { QueryClient } from "@tanstack/react-query";

/**
 * Single source of QueryClient defaults for both the client-side
 * QueryClientProvider and server-side page prefetching (see the
 * server-prefetch-convention skill). Must stay free of "use client" so
 * async server components can call it.
 */
export function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000,
        retry: false,
        refetchOnWindowFocus: false,
        networkMode: "always",
      },
      mutations: {
        networkMode: "always",
        retry: false,
      },
    },
  });
}
