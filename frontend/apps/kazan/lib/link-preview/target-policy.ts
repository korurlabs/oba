import { BlockList, isIP } from "node:net";

/**
 * Raised when a link preview would reach an address that is not on the public internet.
 * The preview runs inside the cluster, so such a request could read cloud metadata,
 * cluster services or other internal endpoints on behalf of the user.
 */
export class LinkPreviewBlockedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "LinkPreviewBlockedError";
  }
}

/**
 * Every IANA special-purpose range that is not globally reachable, plus multicast.
 * Kubernetes pod and service networks live inside the private and shared ranges.
 * IPv4-mapped, IPv4-compatible and NAT64 IPv6 forms are blocked as a whole, because a
 * public web site never needs them and each can carry an internal IPv4 address.
 */
const NON_PUBLIC_RANGES: ReadonlyArray<readonly [string, number, "ipv4" | "ipv6"]> = [
  ["0.0.0.0", 8, "ipv4"],
  ["10.0.0.0", 8, "ipv4"],
  ["100.64.0.0", 10, "ipv4"],
  ["127.0.0.0", 8, "ipv4"],
  ["169.254.0.0", 16, "ipv4"],
  ["172.16.0.0", 12, "ipv4"],
  ["192.0.0.0", 24, "ipv4"],
  ["192.0.2.0", 24, "ipv4"],
  ["192.88.99.0", 24, "ipv4"],
  ["192.168.0.0", 16, "ipv4"],
  ["198.18.0.0", 15, "ipv4"],
  ["198.51.100.0", 24, "ipv4"],
  ["203.0.113.0", 24, "ipv4"],
  ["224.0.0.0", 4, "ipv4"],
  ["240.0.0.0", 4, "ipv4"],
  ["::", 96, "ipv6"],
  ["::ffff:0:0", 96, "ipv6"],
  ["64:ff9b::", 96, "ipv6"],
  ["64:ff9b:1::", 48, "ipv6"],
  ["100::", 64, "ipv6"],
  ["2001::", 23, "ipv6"],
  ["2001:db8::", 32, "ipv6"],
  ["2002::", 16, "ipv6"],
  ["fc00::", 7, "ipv6"],
  ["fe80::", 10, "ipv6"],
  ["fec0::", 10, "ipv6"],
  ["ff00::", 8, "ipv6"],
];

// One list per family: BlockList checks an IPv4 address against IPv6 rules in its mapped
// form, so the ::ffff:0:0/96 rule would otherwise refuse every IPv4 address.
const nonPublicIpv4 = new BlockList();
const nonPublicIpv6 = new BlockList();
for (const [network, prefix, family] of NON_PUBLIC_RANGES) {
  (family === "ipv4" ? nonPublicIpv4 : nonPublicIpv6).addSubnet(network, prefix, family);
}

/** Name suffixes that only resolve inside a private network or the cluster. */
const INTERNAL_NAME_SUFFIXES = [
  ".localhost",
  ".local",
  ".internal",
  ".localdomain",
  ".home.arpa",
  ".lan",
  ".intranet",
  ".corp",
];

/** True only for a literal IPv4 or IPv6 address that is globally reachable. */
export function isPublicAddress(address: string): boolean {
  const family = isIP(address);
  if (family === 4) return !nonPublicIpv4.check(address, "ipv4");
  if (family === 6) return !nonPublicIpv6.check(address, "ipv6");
  return false;
}

/**
 * Parses a user-supplied link and returns it only when it is a plain public web URL:
 * http or https, no credentials, the scheme's default port, and a host that is either a
 * public IP literal or a fully qualified name outside the internal suffixes. A name still
 * has to resolve to public addresses, which the guarded lookup checks at connect time.
 */
export function parsePreviewTarget(raw: string): URL | null {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    // An unparseable link is ordinary input, not a failure: the caller answers with no title.
    return null;
  }

  if (url.protocol !== "http:" && url.protocol !== "https:") return null;
  if (url.username !== "" || url.password !== "") return null;
  // The URL parser drops a port equal to the scheme default, so any port left is non-default.
  if (url.port !== "") return null;

  const host = url.hostname
    .replace(/^\[|\]$/g, "")
    .replace(/\.$/, "")
    .toLowerCase();
  if (isIP(host) !== 0) return isPublicAddress(host) ? url : null;

  // A single-label name resolves through the cluster's search domains to internal services.
  if (!host.includes(".")) return null;
  if (INTERNAL_NAME_SUFFIXES.some((suffix) => host.endsWith(suffix))) return null;

  return url;
}
