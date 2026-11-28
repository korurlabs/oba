import type { AttachmentResponse } from "@oba/shared/features/attachments/schemas";

/** True for a file the browser shows as a picture, so the card can show its thumbnail. */
export function isCampaignContentImage(attachment: Pick<AttachmentResponse, "contentType">): boolean {
  return (attachment.contentType ?? "").toLowerCase().startsWith("image/");
}
