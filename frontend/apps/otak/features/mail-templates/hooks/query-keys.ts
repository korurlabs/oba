import type { MailTemplateQueryRequest } from "../schemas";

/**
 * Query keys of the mail templates. Every key starts with the organization,
 * so the mail templates of one organization never show under another. Kept
 * out of the "use client" hook file so a server page can prefetch under the
 * exact keys the hooks read.
 */
export const MAIL_TEMPLATE_KEYS = {
  all: (organizationId: string) => ["mail-templates", organizationId] as const,
  lists: (organizationId: string) => [...MAIL_TEMPLATE_KEYS.all(organizationId), "list"] as const,
  list: (organizationId: string, params: MailTemplateQueryRequest) =>
    [...MAIL_TEMPLATE_KEYS.lists(organizationId), params] as const,
  detail: (organizationId: string, mailTemplateId: string) =>
    [...MAIL_TEMPLATE_KEYS.all(organizationId), "detail", mailTemplateId] as const,
};
