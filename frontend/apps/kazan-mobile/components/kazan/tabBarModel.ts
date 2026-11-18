/*
 * When the tab bar hides. A chat thread and chat search keep their composer
 * or results down to the bottom edge, as on the web, so the bar gives way on
 * those screens and returns on the way back to the Chat list. The Share pull
 * request form is a full-screen form, and Android draws it inside the tab,
 * so the bar gives way there too, also while its reviewer picker is open.
 */

/** Chat screens, by their route segment under `chat`, that hide the tab bar. */
const CHAT_SCREENS_WITHOUT_TAB_BAR: ReadonlySet<string> = new Set([
  "thread",
  "search",
  "share-pr",
  "share-pr-reviewers",
]);

/**
 * True when the route, as expo-router's `useSegments()` reports it (groups
 * included, such as `["(app)", "(tabs)", "chat", "thread", "[threadId]"]`),
 * is a screen that hides the tab bar.
 */
export function hidesTabBar(segments: readonly string[]): boolean {
  const tabsIndex: number = segments.indexOf("(tabs)");
  if (tabsIndex === -1) return false;
  const tab: string | undefined = segments[tabsIndex + 1];
  const screen: string | undefined = segments[tabsIndex + 2];
  return tab === "chat" && screen !== undefined && CHAT_SCREENS_WITHOUT_TAB_BAR.has(screen);
}
