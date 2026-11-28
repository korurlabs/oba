import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { ACCOUNT_LINKS, ULAK_LINKS, accountOnboardingUrl, appUrl, obaApps } from "./apps";
import { KalkanMark } from "../components/app-marks/kalkan-mark";
import { KayaMark } from "../components/app-marks/kaya-mark";
import { KazanMark } from "../components/app-marks/kazan-mark";
import { OtakMark } from "../components/app-marks/otak-mark";
import { UlakMark } from "../components/app-marks/ulak-mark";

const ENV_KEYS = [
  "NODE_ENV",
  "KAZAN_URL",
  "KAYA_URL",
  "KALKAN_URL",
  "OTAK_URL",
  "ULAK_URL",
  "ACCOUNT_URL",
] as const;

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

/** Replaces the app URL keys of the environment; the links read it on every access. */
function setEnv(env: Partial<Record<(typeof ENV_KEYS)[number], string>>): void {
  for (const key of ENV_KEYS) delete process.env[key];
  Object.assign(process.env, env);
}

describe("appUrl", () => {
  test("production falls back to the korur.com hosts", () => {
    setEnv({ NODE_ENV: "production" });

    expect(appUrl("kazan")).toBe("https://kazan.korur.com");
    expect(appUrl("kaya")).toBe("https://kaya.korur.com");
    expect(appUrl("kalkan")).toBe("https://kalkan.korur.com");
    expect(appUrl("otak")).toBe("https://otak.korur.com");
    expect(appUrl("ulak")).toBe("https://ulak.korur.com");
    expect(appUrl("account")).toBe("https://account.korur.com");
  });

  test("development falls back to the local dev ports", () => {
    setEnv({ NODE_ENV: "development" });

    expect(appUrl("kazan")).toBe("http://localhost:3002");
    expect(appUrl("kaya")).toBe("http://localhost:3014");
    expect(appUrl("kalkan")).toBe("http://localhost:3019");
    expect(appUrl("otak")).toBe("http://localhost:3018");
    expect(appUrl("ulak")).toBe("http://localhost:3016");
    expect(appUrl("account")).toBe("http://localhost:3017");
  });

  test("a configured URL wins and loses its trailing slashes", () => {
    setEnv({
      NODE_ENV: "production",
      ULAK_URL: "https://ulak.example.test//",
      KAZAN_URL: "https://kazan.example.test",
      ACCOUNT_URL: "https://account.example.test/",
      OTAK_URL: "https://otak.example.test/",
      KALKAN_URL: "https://kalkan.example.test///",
    });

    expect(appUrl("ulak")).toBe("https://ulak.example.test");
    expect(appUrl("account")).toBe("https://account.example.test");
    expect(appUrl("kazan")).toBe("https://kazan.example.test");
    expect(appUrl("otak")).toBe("https://otak.example.test");
    expect(appUrl("kalkan")).toBe("https://kalkan.example.test");
    expect(appUrl("kaya")).toBe("https://kaya.korur.com");
  });

  test("an empty configured URL counts as unset", () => {
    setEnv({ NODE_ENV: "production", ULAK_URL: "" });

    expect(appUrl("ulak")).toBe("https://ulak.korur.com");
  });
});

describe("ULAK_LINKS", () => {
  test("the organizations link is the Ulak origin", () => {
    setEnv({ NODE_ENV: "production", ULAK_URL: "https://ulak.example.test/" });

    expect(ULAK_LINKS.organizations).toBe("https://ulak.example.test");
    expect(Object.keys(ULAK_LINKS)).toEqual(["organizations", "azureIntegrations"]);
  });

  test("the Azure integrations link is a path under the Ulak origin", () => {
    setEnv({ NODE_ENV: "production", ULAK_URL: "https://ulak.example.test/" });

    expect(ULAK_LINKS.azureIntegrations).toBe("https://ulak.example.test/integrations/azure");
  });

  test("follow a changed environment on the next read", () => {
    setEnv({ NODE_ENV: "production", ULAK_URL: "https://one.example.test" });
    expect(ULAK_LINKS.organizations).toBe("https://one.example.test");

    setEnv({ NODE_ENV: "production", ULAK_URL: "https://two.example.test" });
    expect(ULAK_LINKS.organizations).toBe("https://two.example.test");
  });
});

describe("ACCOUNT_LINKS", () => {
  test("are paths under the account origin, with the profile at its root", () => {
    setEnv({ NODE_ENV: "production", ACCOUNT_URL: "https://account.example.test/" });

    expect({ ...ACCOUNT_LINKS }).toEqual({
      profile: "https://account.example.test",
      invitations: "https://account.example.test/invitations",
      newOrganization: "https://account.example.test/organizations/new",
      onboarding: "https://account.example.test/onboarding",
    });
  });
});

describe("accountOnboardingUrl", () => {
  test("makes a path absolute against the calling app's base URL", () => {
    setEnv({ NODE_ENV: "production" });

    const url = new URL(accountOnboardingUrl("/home?tab=mine", "https://kazan.korur.com"));

    expect(url.origin + url.pathname).toBe("https://account.korur.com/onboarding");
    expect(url.searchParams.get("callbackUrl")).toBe("https://kazan.korur.com/home?tab=mine");
  });

  test("keeps an absolute callback URL as it is", () => {
    setEnv({ NODE_ENV: "production" });

    const url = new URL(
      accountOnboardingUrl("https://ulak.korur.com/organizations", "https://kazan.korur.com")
    );

    expect(url.searchParams.get("callbackUrl")).toBe("https://ulak.korur.com/organizations");
  });
});

describe("obaApps", () => {
  test("lists Kazan, Kaya, Kalkan, Otak and Ulak in that order, each with its given origin and its mark, and leaves Account out", () => {
    const appUrls = {
      kazan: "https://k.test",
      kaya: "https://y.test",
      kalkan: "https://l.test",
      otak: "https://o.test",
      ulak: "https://a.test",
      account: "https://c.test",
    };

    const apps = obaApps(appUrls);

    expect(apps.map((app) => app.id)).toEqual(["kazan", "kaya", "kalkan", "otak", "ulak"]);
    expect(apps.map((app) => app.url)).toEqual([
      "https://k.test",
      "https://y.test",
      "https://l.test",
      "https://o.test",
      "https://a.test",
    ]);
    expect(apps.map((app) => app.mark)).toEqual([
      KazanMark,
      KayaMark,
      KalkanMark,
      OtakMark,
      UlakMark,
    ]);
  });
});
