import { describe, expect, test } from "bun:test";
import type { LookupAddress } from "node:dns";

import { createGuardedLookup } from "./guarded-lookup";
import type { ResolveAll } from "./guarded-lookup";
import { LinkPreviewBlockedError } from "./target-policy";

function resolvingTo(addresses: LookupAddress[]): ResolveAll {
  return (_hostname, _family, callback) => callback(null, addresses);
}

function lookupWith(
  resolveAll: ResolveAll,
  all: boolean
): Promise<{ error: Error | null; address: string | LookupAddress[]; family?: number }> {
  const lookup = createGuardedLookup(resolveAll);
  return new Promise((resolve) => {
    lookup("example.com", { all }, (error, address, family) => resolve({ error, address, family }));
  });
}

describe("createGuardedLookup", () => {
  test("refuses a name that resolves to the cloud metadata address", async () => {
    const result = await lookupWith(
      resolvingTo([{ address: "169.254.169.254", family: 4 }]),
      false
    );
    expect(result.error).toBeInstanceOf(LinkPreviewBlockedError);
  });

  test("refuses a name when any of its addresses is internal", async () => {
    const result = await lookupWith(
      resolvingTo([
        { address: "93.184.216.34", family: 4 },
        { address: "10.0.0.5", family: 4 },
      ]),
      true
    );
    expect(result.error).toBeInstanceOf(LinkPreviewBlockedError);
  });

  test("refuses a name that resolves to a unique-local IPv6 address", async () => {
    const result = await lookupWith(resolvingTo([{ address: "fd00::1", family: 6 }]), false);
    expect(result.error).toBeInstanceOf(LinkPreviewBlockedError);
  });

  test("refuses a name with no addresses", async () => {
    const result = await lookupWith(resolvingTo([]), false);
    expect(result.error).toBeInstanceOf(LinkPreviewBlockedError);
  });

  test("passes the resolver error through", async () => {
    const failure = Object.assign(new Error("getaddrinfo ENOTFOUND"), { code: "ENOTFOUND" });
    const result = await lookupWith((_hostname, _family, callback) => callback(failure, []), false);
    expect(result.error).toBe(failure);
  });

  test("returns the first public address when one address is asked for", async () => {
    const result = await lookupWith(resolvingTo([{ address: "93.184.216.34", family: 4 }]), false);
    expect(result).toEqual({ error: null, address: "93.184.216.34", family: 4 });
  });

  test("returns every public address when all are asked for", async () => {
    const addresses = [
      { address: "93.184.216.34", family: 4 },
      { address: "2606:2800:220:1::248", family: 6 },
    ];
    const result = await lookupWith(resolvingTo(addresses), true);
    expect(result.error).toBeNull();
    expect(result.address).toEqual(addresses);
  });
});
