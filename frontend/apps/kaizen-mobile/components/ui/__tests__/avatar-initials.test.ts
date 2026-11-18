import { describe, expect, it } from "bun:test";

import { AVATAR_TINTS, avatarPixels, avatarTint, initials } from "@/components/ui/avatar-initials";

describe("initials", () => {
  it("takes the first letter of the first two words, upper-cased", () => {
    expect(initials("Mert Yilmaz")).toBe("MY");
    expect(initials("ayşe nur kaya")).toBe("AN");
    expect(initials("kaizen")).toBe("K");
  });

  it("ignores surrounding and repeated whitespace", () => {
    expect(initials("  Jane   Doe ")).toBe("JD");
  });

  it("falls back to a question mark for an empty name", () => {
    expect(initials("")).toBe("?");
    expect(initials("   ")).toBe("?");
    expect(initials(null)).toBe("?");
  });

  it("keeps a letter outside the basic plane whole", () => {
    expect(initials("𝒜da Lovelace")).toBe("𝒜L");
  });
});

describe("avatarTint", () => {
  it("gives the same name the same tint every time", () => {
    expect(avatarTint("Mert Yilmaz")).toBe(avatarTint("Mert Yilmaz"));
  });

  it("always returns one of the tint pairs", () => {
    for (const name of ["", "a", "Ayşe Kaya", "Korur", "x".repeat(200)]) {
      expect(AVATAR_TINTS).toContain(avatarTint(name));
    }
  });
});

describe("avatarPixels", () => {
  it("maps the named sizes and passes numbers through", () => {
    expect(avatarPixels("sm")).toBe(20);
    expect(avatarPixels("md")).toBe(32);
    expect(avatarPixels("lg")).toBe(64);
    expect(avatarPixels(48)).toBe(48);
  });
});
