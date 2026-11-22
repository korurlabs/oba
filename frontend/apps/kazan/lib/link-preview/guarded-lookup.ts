import { lookup as dnsLookup } from "node:dns";
import type { LookupAddress } from "node:dns";
import type { LookupFunction } from "node:net";

import { isPublicAddress, LinkPreviewBlockedError } from "./target-policy";

/** Resolves every address of a host name. */
export type ResolveAll = (
  hostname: string,
  family: number,
  callback: (error: NodeJS.ErrnoException | null, addresses: LookupAddress[]) => void
) => void;

const resolveWithSystemDns: ResolveAll = (hostname, family, callback) => {
  dnsLookup(hostname, { all: true, family }, callback);
};

/**
 * Builds the socket lookup for link-preview requests. It runs when the socket connects, so
 * the address checked is the address used: a name that resolves to an internal address, or
 * re-resolves to one between a check and the connect (DNS rebinding), is refused. A name
 * with any non-public address is refused as a whole.
 */
export function createGuardedLookup(resolveAll: ResolveAll = resolveWithSystemDns): LookupFunction {
  return (hostname, options, callback) => {
    const family = typeof options.family === "number" ? options.family : 0;
    resolveAll(hostname, family, (error, addresses) => {
      if (error) {
        callback(error, "");
        return;
      }
      if (addresses.length === 0 || addresses.some((entry) => !isPublicAddress(entry.address))) {
        callback(
          new LinkPreviewBlockedError(`Host ${hostname} resolves to a non-public address`),
          ""
        );
        return;
      }
      if (options.all) {
        callback(null, addresses);
        return;
      }
      const [first] = addresses as [LookupAddress, ...LookupAddress[]];
      callback(null, first.address, first.family);
    });
  };
}
