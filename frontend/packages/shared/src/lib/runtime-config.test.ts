import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { getRuntimeConfig, readRuntimeConfig, setBrowserRuntimeConfig, type RuntimeConfig } from "./runtime-config";

const ENV_KEYS = ["NODE_ENV", "API_BASE_URL", "BASE_URL", "KAYA_URL"] as const;
const saved: Record<string, string | undefined> = {};

beforeEach(() => {
  for (const key of ENV_KEYS) saved[key] = process.env[key];
});

afterEach(() => {
  for (const key of ENV_KEYS) {
    if (saved[key] === undefined) delete process.env[key];
    else process.env[key] = saved[key];
  }
});

describe("readRuntimeConfig", () => {
  test("reads the environment on every call, so a changed value needs no rebuild", () => {
    process.env.NODE_ENV = "production";
    process.env.API_BASE_URL = "https://api.one.test";
    process.env.BASE_URL = "https://kaizen.one.test";
    expect(readRuntimeConfig().apiBaseUrl).toBe("https://api.one.test");
    expect(readRuntimeConfig().baseUrl).toBe("https://kaizen.one.test");

    process.env.API_BASE_URL = "https://api.two.test";
    process.env.BASE_URL = "https://kaizen.two.test";
    expect(readRuntimeConfig().apiBaseUrl).toBe("https://api.two.test");
    expect(readRuntimeConfig().baseUrl).toBe("https://kaizen.two.test");
  });

  test("an unset or empty API and base URL stay undefined", () => {
    delete process.env.API_BASE_URL;
    process.env.BASE_URL = "";

    const config: RuntimeConfig = readRuntimeConfig();

    expect(config.apiBaseUrl).toBeUndefined();
    expect(config.baseUrl).toBeUndefined();
  });
});

describe("getRuntimeConfig", () => {
  test("reads the server environment where there is no window", () => {
    process.env.NODE_ENV = "production";
    process.env.KAYA_URL = "https://kaya.example.test/";

    expect(getRuntimeConfig().appUrls.kaya).toBe("https://kaya.example.test");
  });

  test("in the browser, throws before the provider has stored a config and returns it afterwards", () => {
    const global = globalThis as { window?: unknown };
    global.window = {};
    try {
      expect(() => getRuntimeConfig()).toThrow("RuntimeConfigProvider");

      const config: RuntimeConfig = {
        apiBaseUrl: "https://api.example.test",
        baseUrl: "https://kaizen.example.test",
        appUrls: {
          kaizen: "https://kaizen.example.test",
          kaya: "https://kaya.example.test",
          kalkan: "https://kalkan.example.test",
          otak: "https://otak.example.test",
          admin: "https://admin.example.test",
          account: "https://account.example.test",
        },
      };
      setBrowserRuntimeConfig(config);

      expect(getRuntimeConfig()).toBe(config);
    } finally {
      delete global.window;
    }
  });
});
