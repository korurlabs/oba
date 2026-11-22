import { describe, expect, test } from "bun:test";
import { canDeleteRelationAttachment, relationAttachmentSourceText } from "./relation-attachments";

const t = (key: string, values?: Record<string, string>) =>
  values ? `${key}(${Object.values(values).join(",")})` : key;

describe("relationAttachmentSourceText", () => {
  test("a file of the relation reads as uploaded", () => {
    expect(relationAttachmentSourceText({ source: "RELATION", outreachTitle: null }, t)).toBe(
      "source.uploaded"
    );
  });

  test("a mail's file names the outreach", () => {
    expect(relationAttachmentSourceText({ source: "MAIL", outreachTitle: "Offer Q4" }, t)).toBe(
      "source.mail(Offer Q4)"
    );
  });

  test("a mail no outreach names reads as a plain mail", () => {
    expect(relationAttachmentSourceText({ source: "MAIL", outreachTitle: null }, t)).toBe(
      "source.mailUntitled"
    );
  });
});

describe("canDeleteRelationAttachment", () => {
  test("a member removes a file of the relation", () => {
    expect(canDeleteRelationAttachment({ source: "RELATION" }, true)).toBe(true);
  });

  test("a mail's file and a guest's view offer no delete", () => {
    expect(canDeleteRelationAttachment({ source: "MAIL" }, true)).toBe(false);
    expect(canDeleteRelationAttachment({ source: "RELATION" }, false)).toBe(false);
  });
});
