import { describe, expect, it, mock } from "bun:test";

import { SHADOW, shadowStyle } from "@/components/ui/shadow";

// tailwind.config.js reads the hairline width and the preset from nativewind,
// which loads react-native; Bun cannot parse it. Neither affects boxShadow.
mock.module("nativewind/theme", () => ({ hairlineWidth: () => 1 }));
mock.module("nativewind/preset", () => ({}));
const tailwindConfig = (await import("../../../tailwind.config.js")).default as {
  theme: { extend: { boxShadow: Record<string, string> } };
};

describe("SHADOW", () => {
  it("matches the boxShadow entries of tailwind.config.js", () => {
    expect({ ...SHADOW }).toEqual(tailwindConfig.theme.extend.boxShadow);
  });
});

describe("shadowStyle", () => {
  it("returns the boxShadow of the token in light mode", () => {
    expect(shadowStyle("card", "light")).toEqual({ boxShadow: SHADOW.card });
    expect(shadowStyle("dialog", undefined)).toEqual({ boxShadow: SHADOW.dialog });
  });

  it("draws no shadow in dark mode or without a token", () => {
    expect(shadowStyle("card", "dark")).toBeUndefined();
    expect(shadowStyle(null, "light")).toBeUndefined();
  });
});
