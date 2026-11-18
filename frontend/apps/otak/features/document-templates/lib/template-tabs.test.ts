import { describe, expect, test } from "bun:test";
import { isTemplatePageTab, loadTemplatePageTabParams } from "./template-tabs";

describe("template page tabs", () => {
  test("an empty URL opens the document templates", () => {
    expect(loadTemplatePageTabParams("").tab).toBe("documents");
  });

  test("the mail tab is read from the URL, an unknown tab falls back", () => {
    expect(loadTemplatePageTabParams("?tab=mail").tab).toBe("mail");
    expect(loadTemplatePageTabParams("?tab=letters").tab).toBe("documents");
  });

  test("only the known tabs are tabs", () => {
    expect(isTemplatePageTab("mail")).toBe(true);
    expect(isTemplatePageTab("documents")).toBe(true);
    expect(isTemplatePageTab("MAIL")).toBe(false);
  });
});
