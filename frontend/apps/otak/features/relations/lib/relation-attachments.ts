import type { RelationAttachment } from "../schemas";

/**
 * Where a relation's file comes from: "Uploaded" for a file of the relation
 * itself, else "Mail: <outreach title>", or a plain "Mail" when no outreach
 * names the mail. `t` is the `relations.attachments` translator.
 */
export function relationAttachmentSourceText(
  attachment: Pick<RelationAttachment, "source" | "outreachTitle">,
  t: (key: string, values?: Record<string, string>) => string
): string {
  if (attachment.source === "RELATION") {
    return t("source.uploaded");
  }
  return attachment.outreachTitle === null
    ? t("source.mailUntitled")
    : t("source.mail", { title: attachment.outreachTitle });
}

/**
 * Only a file of the relation itself is removed here; a mail's file belongs
 * to the mail and is removed there, while the mail can still change.
 */
export function canDeleteRelationAttachment(
  attachment: Pick<RelationAttachment, "source">,
  isMember: boolean
): boolean {
  return isMember && attachment.source === "RELATION";
}
