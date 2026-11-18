import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { SessionDetailResponse } from "../schemas";
import { configReleaseView } from "./config-release";
import { ConfigReleaseBadge } from "./config-release-badge";

const BASE = {
  repositoryId: "repo-1",
  releaseNumber: null,
  releaseState: null,
  deliveredBy: null,
} satisfies Parameters<typeof configReleaseView>[0];

describe("configReleaseView", () => {
  test("a matched release links to its delivered paths", () => {
    const view = configReleaseView({
      ...BASE,
      releaseNumber: 7,
      releaseState: "FRESH",
      deliveredBy: "LAUNCHER",
    });
    expect(view.label).toBe("Config release 7");
    expect(view.href).toBe("/rules?scope=repository%3Arepo-1&release=7");
    expect(view.state?.text).toBe("Fresh");
    expect(view.state?.variant).toBe("success");
    expect(view.deliveredBy).toBe("Delivered by the launcher");
  });

  test("cached and stale keep the release and say why", () => {
    const cached = configReleaseView({
      ...BASE,
      releaseNumber: 6,
      releaseState: "CACHED",
    });
    expect(cached.href).not.toBeNull();
    expect(cached.state?.description).toContain("cached on the machine");
    const stale = configReleaseView({
      ...BASE,
      releaseNumber: 6,
      releaseState: "STALE",
      deliveredBy: "HOOK",
    });
    expect(stale.state?.variant).toBe("warning");
    expect(stale.state?.description).toContain("waiting for a restart");
    expect(stale.deliveredBy).toBe("Delivered by the SessionStart hook");
  });

  test("an unverified hash names no release and links nowhere", () => {
    const view = configReleaseView({ ...BASE, releaseState: "UNVERIFIED" });
    expect(view.label).toBe("Unknown config release");
    expect(view.href).toBeNull();
    expect(view.state?.variant).toBe("error");
  });

  test("NONE is a session without a release, unlike a session that reported nothing", () => {
    expect(configReleaseView({ ...BASE, releaseState: "NONE" }).label).toBe("No config release");
    const unreported = configReleaseView(BASE);
    expect(unreported.label).toBe("No config release reported");
    expect(unreported.state).toBeNull();
  });
});

describe("ConfigReleaseBadge", () => {
  test("renders the number as a link, the state and the delivery", () => {
    const html = renderToStaticMarkup(
      <ConfigReleaseBadge
        detail={
          {
            ...BASE,
            releaseNumber: 3,
            releaseState: "STALE",
            deliveredBy: "HOOK",
          } as SessionDetailResponse
        }
      />
    );
    expect(html).toContain('href="/rules?scope=repository%3Arepo-1&amp;release=3"');
    expect(html).toContain("Config release 3");
    expect(html).toContain("Stale");
    expect(html).toContain("Delivered by the SessionStart hook");
  });

  test("an unreported release renders no link and no state", () => {
    const html = renderToStaticMarkup(
      <ConfigReleaseBadge detail={BASE as SessionDetailResponse} />
    );
    expect(html).toContain("No config release reported");
    expect(html).not.toContain("href=");
  });
});
