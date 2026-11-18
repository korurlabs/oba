import { describe, expect, test } from "bun:test";

import nextConfig from "./next.config.mjs";

type Redirect = {
  source: string;
  has?: { type: string; key: string; value?: string }[];
  missing?: { type: string; key: string }[];
  destination: string;
  permanent: boolean;
};

async function redirects(): Promise<Redirect[]> {
  const list = await nextConfig.redirects?.();
  return (list ?? []) as Redirect[];
}

describe("the /conventions redirect", () => {
  test("a request with a tab goes to /rules, as a temporary redirect", async () => {
    const rules = (await redirects()).filter((entry: Redirect) => entry.destination === "/rules");
    expect(rules).toEqual([
      {
        source: "/conventions",
        has: [{ type: "query", key: "tab" }],
        destination: "/rules",
        permanent: false,
      },
    ]);
  });

  test("only a request that names a tab matches, so /conventions alone stays", async () => {
    const list: Redirect[] = await redirects();
    const forConventions = list.filter((entry: Redirect) => entry.source === "/conventions");
    expect(forConventions.length).toBeGreaterThan(0);
    for (const entry of forConventions) {
      expect(entry.has).toEqual([{ type: "query", key: "tab" }]);
    }
  });

  test("the destination names no query, so Next.js passes the request's query on", async () => {
    for (const entry of await redirects()) {
      if (entry.source === "/conventions") expect(entry.destination).not.toContain("?");
    }
  });
});

describe("the agent to machine redirects", () => {
  test("the old agents pages answer 308 to the machines pages", async () => {
    const list: Redirect[] = await redirects();
    expect(list).toContainEqual({ source: "/agents", destination: "/machines", permanent: true });
    expect(list).toContainEqual({
      source: "/agents/:id",
      destination: "/machines/:id",
      permanent: true,
    });
  });

  test("the wall's agentId filter becomes machineId, and stops matching once it is set", async () => {
    const wall = (await redirects()).filter((entry: Redirect) => entry.source === "/");
    expect(wall).toEqual([
      {
        source: "/",
        has: [{ type: "query", key: "agentId", value: "(?<agentId>.+)" }],
        missing: [{ type: "query", key: "machineId" }],
        destination: "/?machineId=:agentId",
        permanent: true,
      },
    ]);
  });
});
