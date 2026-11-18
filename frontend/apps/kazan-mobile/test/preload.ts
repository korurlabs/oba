import { mock } from "bun:test";

/**
 * `react-native` itself cannot load under `bun test` (it ships Flow syntax),
 * so the tests stub it with `mock.module`. Bun fixes a mocked module's export
 * names the first time any file links against it, for the whole run, and the
 * file order differs between machines. A test file that stubs fewer names
 * than a later file imports then fails that later file with "Export named
 * 'Linking' not found". This preload stubs every name the tested code
 * imports and links the stub at once, before any test file loads, so the
 * export set is always complete; each test file still replaces the values
 * with its own `mock.module("react-native", ...)`.
 */
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { currentState: "active", addEventListener: () => ({ remove: () => undefined }) },
  Linking: { openSettings: async () => undefined },
}));

// Linking now, not on the first import of a test file, which may stub fewer names.
await import("react-native");
