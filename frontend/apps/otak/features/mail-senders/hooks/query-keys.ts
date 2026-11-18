/**
 * Query keys of the mail senders. Every key starts with the organization, so
 * the senders of one organization never show under another. Kept out of the
 * "use client" hook file so a server page can prefetch under the exact keys
 * the hooks read.
 */
export const MAIL_SENDER_KEYS = {
  all: (organizationId: string) => ["mail-senders", organizationId] as const,
  list: (organizationId: string) => [...MAIL_SENDER_KEYS.all(organizationId), "list"] as const,
};
