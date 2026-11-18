import { hostOf, ticketKeyFromLink } from "@/components/ticket/ticketModel";
import { ticketPath } from "@/components/ticket/ticketRoutes";
import { ENV } from "@/lib/env";
import {
  kazanLinkDirectPath,
  linkScreenPath,
  parseKazanLink,
  type KazanLinkTarget,
} from "@/lib/kazanLinks";
import { HOME_PATH, isAppRootLink, isDevClientLink } from "@/lib/systemLinks";

/**
 * Turns the links the system hands to the app into app paths:
 *
 * - `kazan://ticket/{key}` and a Kazan web ticket page open the ticket screen;
 * - any other Kazan web page the app has a screen for opens it: a fixed
 *   screen or form directly, and a board, a channel, a message or a thread
 *   through the link screen, which looks up its slug or channel first.
 *
 * Every other link is left as it came.
 *
 * The root URL, which the app also starts on without a link, opens Home
 * directly. The signed-in stack always keeps the tabs under its first screen,
 * so the root route would land above the tabs, and its redirect to Home would
 * then replace it with a second copy of the tabs.
 *
 * The development client's own URL names no screen. When it starts the app it
 * opens Home like the root URL; while the app runs it returns null, which
 * Expo Router reads as "stay on the current screen", so returning from another
 * app does not stack a second copy of the tabs over the open screen.
 *
 * A throw here would crash the app on launch, so a failure is logged and the
 * link goes through unchanged, where the router shows its unmatched screen.
 */
export function redirectSystemPath({
  path,
  initial,
}: {
  path: string;
  initial: boolean;
}): string | null {
  try {
    if (isDevClientLink(path)) return initial ? HOME_PATH : null;
    if (isAppRootLink(path)) return HOME_PATH;
    const kazanWebHost: string | null = hostOf(ENV.kazanWebUrl);
    const ticketKey: string | null = ticketKeyFromLink(path, kazanWebHost);
    if (ticketKey) return ticketPath(ticketKey);
    const target: KazanLinkTarget | null = parseKazanLink(path, kazanWebHost);
    if (!target) return path;
    return kazanLinkDirectPath(target) ?? linkScreenPath(path);
  } catch (error: unknown) {
    console.warn(
      "[links] could not read link",
      path,
      error instanceof Error ? error.message : error
    );
    return path;
  }
}
