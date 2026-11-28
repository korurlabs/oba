import { describe, expect, test } from "bun:test";
import { isMailPageTab, loadMailPageTabParams } from "./mail-page-tabs";

describe("loadMailPageTabParams", () => {
  test("an empty URL opens the templates tab", () => {
    expect(loadMailPageTabParams("").tab).toBe("templates");
  });

  test("the senders tab is read from the URL", () => {
    expect(loadMailPageTabParams("?tab=senders").tab).toBe("senders");
  });

  test("an unknown tab falls back to the templates tab", () => {
    expect(loadMailPageTabParams("?tab=outreaches").tab).toBe("templates");
  });
});

describe("isMailPageTab", () => {
  test("only the page's tabs are tabs", () => {
    expect(isMailPageTab("templates")).toBe(true);
    expect(isMailPageTab("senders")).toBe(true);
    expect(isMailPageTab("outreaches")).toBe(false);
  });
});
