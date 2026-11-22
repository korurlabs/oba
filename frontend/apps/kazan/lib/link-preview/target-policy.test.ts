import { describe, expect, test } from "bun:test";

import { isPublicAddress, parsePreviewTarget } from "./target-policy";

describe("isPublicAddress", () => {
  test.each([
    "127.0.0.1",
    "127.0.0.2",
    "10.1.2.3",
    "172.16.0.1",
    "172.31.255.255",
    "192.168.1.1",
    "169.254.169.254",
    "100.64.0.1",
    "0.0.0.0",
    "224.0.0.1",
    "255.255.255.255",
    "::",
    "::1",
    "::ffff:127.0.0.1",
    "::ffff:7f00:1",
    "64:ff9b::a9fe:a9fe",
    "fc00::1",
    "fd12:3456::1",
    "fe80::1",
    "ff02::1",
    "2001:db8::1",
    "not-an-ip",
  ])("refuses %s", (address) => {
    expect(isPublicAddress(address)).toBe(false);
  });

  test.each([
    "93.184.216.34",
    "8.8.8.8",
    "172.32.0.1",
    "2606:4700::6810:84e5",
    "2a00:1450:4001::200e",
  ])("allows %s", (address) => {
    expect(isPublicAddress(address)).toBe(true);
  });
});

describe("parsePreviewTarget", () => {
  test.each([
    "ftp://example.com/",
    "file:///etc/passwd",
    "gopher://example.com/",
    "javascript:alert(1)",
    "http://user:pass@example.com/",
    "http://user@example.com/",
    "http://example.com:8080/",
    "https://example.com:80/",
    "http://localhost/",
    "http://localhost./",
    "http://kazan-api/",
    "http://kazan-api.oba.svc.cluster.local/",
    "http://metadata.google.internal/",
    "http://printer.local/",
    "http://127.0.0.1/",
    "http://2130706433/",
    "http://0x7f000001/",
    "http://0177.0.0.1/",
    "http://169.254.169.254/latest/meta-data/",
    "http://[::1]/",
    "http://[::ffff:127.0.0.1]/",
    "http://[fd00::1]/",
    "http://10.0.0.1/",
    "not a url",
  ])("refuses %s", (raw) => {
    expect(parsePreviewTarget(raw)).toBeNull();
  });

  test.each([
    "https://example.com/page",
    "http://example.com:80/",
    "https://example.com:443/a?b=c",
    "http://93.184.216.34/",
  ])("allows %s", (raw) => {
    expect(parsePreviewTarget(raw)).not.toBeNull();
  });
});
