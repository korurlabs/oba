import { describe, expect, it } from "bun:test";

import { offlineBannerPlacement } from "@/components/kaizen/offlineBannerModel";

describe("offlineBannerPlacement", () => {
  it("puts the strip under the native bar of a pushed screen", () => {
    expect(offlineBannerPlacement({})).toBe("underHeader");
    expect(offlineBannerPlacement({ headerShown: true })).toBe("underHeader");
    expect(offlineBannerPlacement({ presentation: "card" })).toBe("underHeader");
  });

  it("leaves a screen that draws its own title row to mount the strip itself", () => {
    expect(offlineBannerPlacement({ headerShown: false })).toBe("none");
  });

  it("shows no strip on a sheet or a modal", () => {
    expect(offlineBannerPlacement({ presentation: "formSheet" })).toBe("none");
    expect(offlineBannerPlacement({ presentation: "transparentModal" })).toBe("none");
    expect(offlineBannerPlacement({ presentation: "fullScreenModal", headerShown: true })).toBe(
      "none"
    );
  });
});
