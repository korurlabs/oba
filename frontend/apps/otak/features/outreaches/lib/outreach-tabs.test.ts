import { describe, expect, test } from "bun:test";
import { isOutreachPageTab, loadOutreachPageTabParams } from "./outreach-tabs";

describe("loadOutreachPageTabParams", () => {
  test("an empty URL opens the outreaches tab", () => {
    expect(loadOutreachPageTabParams("").tab).toBe("outreaches");
  });

  test("the senders tab is read from the URL", () => {
    expect(loadOutreachPageTabParams("?tab=senders").tab).toBe("senders");
  });

  test("an unknown tab falls back to the outreaches tab", () => {
    expect(loadOutreachPageTabParams("?tab=templates").tab).toBe("outreaches");
  });
});

describe("isOutreachPageTab", () => {
  test("only the page's tabs are tabs", () => {
    expect(isOutreachPageTab("outreaches")).toBe(true);
    expect(isOutreachPageTab("senders")).toBe(true);
    expect(isOutreachPageTab("leads")).toBe(false);
  });
});
