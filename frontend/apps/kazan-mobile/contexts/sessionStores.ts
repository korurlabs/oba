import { clearPullRequestFilters } from "@/components/pull-requests/pullRequestFilterStore";
import { clearNudges } from "@/components/updates/nudgeStore";
import { clearUpdateFilters } from "@/components/updates/updateFilterStore";
import { clearChatMessageVersions } from "@/hooks/queries/chatCache";
import { clearChatOutbox } from "@/hooks/queries/chatOutbox";

/**
 * Forgets what the signed-in user left in memory while the app ran: the
 * pull request and daily update filters, the members they nudged, their
 * unsent chat messages and the message versions and deletions the chat
 * signals reported. These stores are kept per organization, not per user,
 * so without this the next person to sign in on the phone would start with
 * them, and could send the previous user's unsent messages as their own.
 */
export function clearSessionStores(): void {
  clearPullRequestFilters();
  clearUpdateFilters();
  clearNudges();
  clearChatOutbox();
  clearChatMessageVersions();
}
