import { describe, expect, it } from "bun:test";

import { IMAGE_VIEWER_STATUS_BAR_STYLE, statusBarStyleFor } from "@/lib/statusBar";

describe("statusBarStyleFor", () => {
  it("gives the dark theme a light clock and icons", () => {
    expect(statusBarStyleFor("dark")).toBe("light");
  });

  it("gives the light theme a dark clock and icons", () => {
    expect(statusBarStyleFor("light")).toBe("dark");
  });

  it("treats an unknown scheme as light, the app's first frame", () => {
    expect(statusBarStyleFor(undefined)).toBe("dark");
  });
});

describe("IMAGE_VIEWER_STATUS_BAR_STYLE", () => {
  it("is light, since the viewers are black in both themes", () => {
    expect(IMAGE_VIEWER_STATUS_BAR_STYLE).toBe("light");
  });
});
