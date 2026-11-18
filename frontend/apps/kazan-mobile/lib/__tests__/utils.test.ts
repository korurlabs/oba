import { describe, expect, it } from "bun:test";

import { cn } from "@/lib/utils";

describe("cn", () => {
  it("keeps a type-scale size next to a text colour", () => {
    expect(cn("text-foreground", "text-body")).toBe("text-foreground text-body");
    expect(cn("text-muted-foreground text-ticket-key")).toBe(
      "text-muted-foreground text-ticket-key"
    );
  });

  it("lets a later type-scale size replace an earlier one", () => {
    expect(cn("text-body", "text-footnote")).toBe("text-footnote");
  });

  it("lets a later colour replace an earlier one", () => {
    expect(cn("text-foreground text-body", "text-link")).toBe("text-body text-link");
  });

  it("keeps the hairline border width next to a border colour", () => {
    expect(cn("border-hairline", "border-border")).toBe("border-hairline border-border");
  });

  it("lets a later font family replace an earlier one", () => {
    expect(cn("font-sans", "font-sans-semibold")).toBe("font-sans-semibold");
  });
});
