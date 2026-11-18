"use client";

import { createContext, useContext } from "react";
import { setBrowserRuntimeConfig, type RuntimeConfig } from "../lib/runtime-config";

const RuntimeConfigContext = createContext<RuntimeConfig | null>(null);

/**
 * Hands the deployment's public URLs, read by the root layout on the server, to
 * the client code. The browser copy is stored during render rather than in an
 * effect, so the children's first render and their first API calls already
 * see it. Storing the same value again on a re-render is harmless.
 */
export function RuntimeConfigProvider({
  config,
  children,
}: {
  config: RuntimeConfig;
  children: React.ReactNode;
}) {
  if (typeof window !== "undefined") {
    setBrowserRuntimeConfig(config);
  }
  return <RuntimeConfigContext.Provider value={config}>{children}</RuntimeConfigContext.Provider>;
}

/** The deployment's public URLs; throws outside `RuntimeConfigProvider`, which is a wiring error. */
export function useRuntimeConfig(): RuntimeConfig {
  const config: RuntimeConfig | null = useContext(RuntimeConfigContext);
  if (!config) {
    throw new Error("[runtime-config] useRuntimeConfig called outside RuntimeConfigProvider");
  }
  return config;
}
