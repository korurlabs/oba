import { describe, expect, it } from "bun:test";

import type { TicketAttachmentMessageKey } from "@/components/ticket/ticketAttachmentsModel";
import en from "@/i18n/locales/en.json";
import nl from "@/i18n/locales/nl.json";
import tr from "@/i18n/locales/tr.json";

/**
 * Every message the attachment model hands to the screen, with the values
 * its text must show. The record type fails the type check when the model
 * gains a key this list lacks.
 */
const PLACEHOLDERS = {
  ticketAttachmentTypeNotAllowed: ["{{name}}"],
  ticketAttachmentFileTooLarge: ["{{name}}", "{{max}}"],
  ticketAttachmentBatchTooLarge: ["{{name}}", "{{max}}"],
  ticketAttachmentsDuplicate: ["{{name}}"],
  ticketAttachmentsEmptyFile: ["{{name}}"],
  ticketAttachmentsTooLarge: ["{{max}}"],
  ticketAttachmentsForbidden: [],
  ticketAttachmentsNotFound: [],
  ticketAttachmentsNetwork: [],
  ticketAttachmentsLoadFailed: [],
  ticketAttachmentsUploadFailed: [],
  ticketAttachmentsDeleteFailed: [],
} as const satisfies Record<TicketAttachmentMessageKey, readonly string[]>;

const LOCALES = { en, nl, tr } as Record<string, Record<string, string>>;

describe("ticket attachment messages", () => {
  for (const [locale, messages] of Object.entries(LOCALES)) {
    it(`${locale} has every model message with its values`, () => {
      for (const [key, placeholders] of Object.entries(PLACEHOLDERS)) {
        const text: string | undefined = messages[key];
        expect({ key, present: typeof text === "string" }).toEqual({ key, present: true });
        for (const placeholder of placeholders) {
          expect({ key, placeholder, shown: text?.includes(placeholder) }).toEqual({
            key,
            placeholder,
            shown: true,
          });
        }
      }
    });

    it(`${locale} has the fallback for a refusal that names no file`, () => {
      expect(messages.ticketAttachmentsSomeFile?.trim()).toBeTruthy();
    });
  }
});
