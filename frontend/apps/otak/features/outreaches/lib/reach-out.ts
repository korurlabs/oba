import { ContactEmailType, type ContactListItem } from "@/features/contacts/schemas";
import type { DocumentLanguage } from "@/features/document-templates/schemas";
import type { MailTemplateDetail } from "@/features/mail-templates/schemas";
import {
  RELATION_LIMITS,
  RelationType,
  type RelationQueryRequest,
} from "@/features/relations/schemas";
import {
  DEFAULT_MAIL_LANGUAGE,
  OUTREACH_LIMITS,
  OutreachType,
  type OutreachRequest,
} from "../schemas";

/** The most leads one search of the lead picker shows. */
export const LEAD_PICKER_SIZE = 20;

/**
 * The relation query of the lead picker's search: the first leads whose
 * name matches, as only a lead is reached out to.
 */
export function leadPickerQuery(search: string): RelationQueryRequest {
  const trimmed = search.trim().slice(0, RELATION_LIMITS.searchMax);
  return {
    type: RelationType.LEAD,
    ...(trimmed ? { search: trimmed } : {}),
    page: 1,
    amount: LEAD_PICKER_SIZE,
  };
}

/** Every step of reaching out to a lead, in order; a WHATSAPP or CALL skips the mail steps. */
export const REACH_OUT_STEPS = ["channel", "contact", "mail", "attachments", "save"] as const;
export type ReachOutStep = (typeof REACH_OUT_STEPS)[number];

/** The steps a mail needs and the other channels do not. */
const MAIL_ONLY_STEPS: readonly ReachOutStep[] = ["mail", "attachments"];

/** The steps of a reach-out on this channel, in order. */
export function reachOutSteps(type: OutreachType): readonly ReachOutStep[] {
  return type === OutreachType.MAIL
    ? REACH_OUT_STEPS
    : REACH_OUT_STEPS.filter((step) => !MAIL_ONLY_STEPS.includes(step));
}

/** The mail template a reach-out starts from, with the content it had when picked. */
export interface ReachOutTemplate {
  id: string;
  name: string;
  subject: string;
  body: string;
  language: DocumentLanguage;
}

/**
 * What the user has chosen so far. `source` is null until the mail step is
 * answered; "scratch" means a mail written by hand. The mail fields count
 * only for a MAIL.
 */
export interface ReachOutSelection {
  type: OutreachType;
  contactId: string | null;
  source: ReachOutTemplate | "scratch" | null;
  subject: string;
  body: string;
  language: DocumentLanguage;
  title: string;
  /** The campaign the outreach belongs to; null for none. */
  campaignId: string | null;
}

export const emptyReachOutSelection = (): ReachOutSelection => ({
  type: OutreachType.MAIL,
  contactId: null,
  source: null,
  subject: "",
  body: "",
  language: DEFAULT_MAIL_LANGUAGE,
  title: "",
  campaignId: null,
});

/**
 * The contacts a reach-out can go to: those with a business address. A
 * private address (type PERSONAL) is left out, so a sales mail never goes to
 * someone's own inbox; an address without a type is kept, since most
 * contacts were stored before the type existed.
 */
export function mailableContacts(contacts: readonly ContactListItem[]): ContactListItem[] {
  return contacts.filter(
    (contact) =>
      (contact.email ?? "").trim() !== "" && contact.emailType !== ContactEmailType.PERSONAL
  );
}

/** The contacts a WHATSAPP message or a call can go to: those with a phone number. */
export function callableContacts(contacts: readonly ContactListItem[]): ContactListItem[] {
  return contacts.filter((contact) => (contact.phone ?? "").trim() !== "");
}

/** The contacts a reach-out on this channel can go to. */
export function reachableContacts(
  contacts: readonly ContactListItem[],
  type: OutreachType
): ContactListItem[] {
  return type === OutreachType.MAIL ? mailableContacts(contacts) : callableContacts(contacts);
}

/** Starts the mail from a mail template: its subject, body and language, and its name as the title if none is typed yet. */
export function applyTemplate(
  selection: ReachOutSelection,
  template: Pick<MailTemplateDetail, "id" | "name" | "subject" | "body" | "language">
): ReachOutSelection {
  const source: ReachOutTemplate = {
    id: template.id,
    name: template.name,
    subject: template.subject,
    body: template.body,
    language: template.language,
  };
  return {
    ...selection,
    source,
    subject: source.subject,
    body: source.body,
    language: source.language,
    title:
      selection.title.trim() === ""
        ? template.name.slice(0, OUTREACH_LIMITS.titleMax)
        : selection.title,
  };
}

/** Starts an empty mail written by hand, in the default language. */
export function startFromScratch(selection: ReachOutSelection): ReachOutSelection {
  return {
    ...selection,
    source: "scratch",
    subject: "",
    body: "",
    language: DEFAULT_MAIL_LANGUAGE,
  };
}

/** Whether the mail has a subject and a body within the server's limits. */
export function isMailValid(selection: Pick<ReachOutSelection, "subject" | "body">): boolean {
  return (
    selection.subject.trim() !== "" &&
    selection.subject.length <= OUTREACH_LIMITS.subjectMax &&
    selection.body.trim() !== "" &&
    selection.body.length <= OUTREACH_LIMITS.bodyMax
  );
}

/** Whether the title is set and within the server's limit. */
export function isTitleValid(title: string): boolean {
  const trimmed = title.trim();
  return trimmed !== "" && trimmed.length <= OUTREACH_LIMITS.titleMax;
}

/**
 * Whether the step is answered. `contacts` are the contacts reachable on the
 * chosen channel; the contact must still be one of them, since the list can
 * change while the dialog is open. The channel always has a value and the
 * attachments are optional, so those steps are always answered.
 */
export function isStepComplete(
  step: ReachOutStep,
  selection: ReachOutSelection,
  contacts: readonly ContactListItem[]
): boolean {
  switch (step) {
    case "channel":
      return true;
    case "contact":
      return contacts.some((contact) => contact.id === selection.contactId);
    case "mail":
      return selection.source !== null && isMailValid(selection);
    case "attachments":
      return true;
    case "save":
      return (
        isTitleValid(selection.title) &&
        reachOutSteps(selection.type)
          .filter((earlier) => earlier !== "save")
          .every((earlier) => isStepComplete(earlier, selection, contacts))
      );
  }
}

/** The step after this one on the channel, or null on the last. */
export function nextReachOutStep(step: ReachOutStep, type: OutreachType): ReachOutStep | null {
  const steps = reachOutSteps(type);
  return steps[steps.indexOf(step) + 1] ?? null;
}

/** The step before this one on the channel, or null on the first. */
export function previousReachOutStep(step: ReachOutStep, type: OutreachType): ReachOutStep | null {
  const steps = reachOutSteps(type);
  return steps[steps.indexOf(step) - 1] ?? null;
}

/** Whether the mail still reads as the template it started from. */
export function isTemplateUnchanged(selection: ReachOutSelection): boolean {
  const source = selection.source;
  return (
    source !== null &&
    source !== "scratch" &&
    selection.subject === source.subject &&
    selection.body === source.body &&
    selection.language === source.language
  );
}

/**
 * The create request of a complete selection. A WHATSAPP or CALL goes
 * without a mail, which the server refuses on those channels.
 * For a MAIL, an untouched template goes by its id, so the server copies it
 * and the mail keeps its link to the template; an edited one, or a mail
 * written by hand, goes as the text typed.
 */
export function toReachOutRequest(
  relationId: string,
  selection: ReachOutSelection
): OutreachRequest {
  const base = {
    relationId,
    contactId: selection.contactId ?? "",
    title: selection.title.trim(),
    type: selection.type,
    campaignId: selection.campaignId,
  };
  if (selection.type !== OutreachType.MAIL) {
    return { ...base, mailTemplateId: null, mail: null };
  }
  if (
    isTemplateUnchanged(selection) &&
    selection.source !== null &&
    selection.source !== "scratch"
  ) {
    return { ...base, mailTemplateId: selection.source.id, mail: null };
  }
  return {
    ...base,
    mailTemplateId: null,
    mail: { subject: selection.subject, body: selection.body, language: selection.language },
  };
}
