"use client";

import { useDocumentTitleCount } from "@/hooks/use-document-title";
import { useSidebarChatUnreadCount } from "./sidebar-chat-unread";

/**
 * Shows the reader's unread chat messages in front of the tab title on every
 * dashboard page, "(4) Create Ticket | Korur", and nothing at 0, so a message
 * is noticed from another tab. The count is the sum of the sidebar's chat
 * badges, capped for display the same way, and follows them live.
 *
 * It is mounted by the dashboard layout, next to the sidebar and not inside
 * it: below 768px the sidebar is an off-canvas sheet whose content is
 * unmounted while the sheet is closed. Renders nothing.
 */
export function ChatUnreadTitle() {
  const { count, shownCount } = useSidebarChatUnreadCount();
  useDocumentTitleCount(count > 0 ? shownCount : null);
  return null;
}
