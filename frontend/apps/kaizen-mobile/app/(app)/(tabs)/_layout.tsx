import { useSegments } from "expo-router";
import { NativeTabs } from "expo-router/unstable-native-tabs";
import { Platform } from "react-native";

import { hidesTabBar } from "@/components/kaizen/tabBarModel";
import { formatBadgeCount } from "@/components/ui/badge-count";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatUnread } from "@/hooks/queries/chat";
import { useChatChannelListRealtime } from "@/hooks/queries/chatRealtime";
import { useUrgentIncidentCount } from "@/hooks/queries/tickets";
import { useMe } from "@/hooks/queries/users";
import { useTranslation } from "@/i18n";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { useRealtimeSession } from "@/lib/realtime";

/**
 * The five tabs of the signed-in app, drawn by the platform's own tab bar.
 * Each badge is a count the user should act on: open P1 and P2 incidents,
 * unread chat messages and pending invitations; a zero hides it.
 *
 * A tab whose folder has only `index.tsx` is the route `<tab>/index`; once the
 * tab gets its own `_layout.tsx`, its trigger name becomes `<tab>`. Incidents
 * has no stack of its own: an incident and Report incident open in the app
 * stack above the tabs.
 *
 * The bar hides on a chat thread, on chat search and under the Share pull
 * request form (`hidesTabBar`).
 * NativeTabs in this Expo version hides the bar only as a whole, through its
 * `hidden` prop, and has no per-screen option, so the layout derives it from
 * the focused route.
 *
 * The iOS bar floats over the bottom of each tab, and the tab's safe area
 * includes it. A list that reaches the bottom of the screen therefore sets
 * `contentInsetAdjustmentBehavior="automatic"`, so UIKit insets its content by
 * that safe area and the last row scrolls clear of the bar, also when the bar
 * hides. React Native defaults to "never", and the tab screen restores
 * "automatic" only for a scroll view at the top of its first-child chain, so a
 * list under a toolbar or a segmented control does not get it on its own. A
 * view pinned to the bottom, such as a chat composer, sits in a SafeAreaView
 * with the bottom edge, whose inset inside a tab includes the bar. Android
 * stops each tab's content above its bar, so neither adds padding there.
 *
 * VoiceOver and TalkBack read a tab with a badge by its own label, which
 * names what the count counts; the Chat label also gives the notification
 * total (mentions and replies), which the badge leaves out.
 *
 * The organization's realtime session and the chat channel list signals are
 * held here rather than in the Chat tab, so the chat badge is live from app
 * start instead of from the first visit to the Chat tab.
 */
export default function TabsLayout() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const tabBarHidden: boolean = hidesTabBar(useSegments());
  useRealtimeSession(useActiveOrganizationId());
  useChatChannelListRealtime();
  const incidentCount: number = useUrgentIncidentCount().data ?? 0;
  const chatUnread = useChatUnread().data;
  const invitationCount: number = useMe(true).data?.pendingInvitations?.length ?? 0;
  const incidentBadge: string | null = formatBadgeCount(incidentCount);
  const chatBadge: string | null = formatBadgeCount(chatUnread?.total);
  const invitationBadge: string | null = formatBadgeCount(invitationCount);

  /** The tab's name, then each count it carries, as one spoken label. */
  const spoken = (...parts: (string | null)[]): string =>
    parts.filter((part): part is string => !!part).join(", ");
  const incidentsLabel: string = spoken(
    t("tabIncidents"),
    incidentBadge ? t("tabIncidentsBadge", { count: incidentCount, value: incidentBadge }) : null
  );
  const chatLabel: string = spoken(
    t("tabChat"),
    chatBadge ? t("tabChatUnread", { count: chatUnread?.total ?? 0, value: chatBadge }) : null,
    chatUnread && chatUnread.mentionTotal > 0
      ? t("tabChatMentions", { count: chatUnread.mentionTotal })
      : null
  );
  const accountLabel: string = spoken(
    t("tabAccount"),
    invitationBadge
      ? t("tabAccountInvitations", { count: invitationCount, value: invitationBadge })
      : null
  );

  return (
    <NativeTabs
      tintColor={colors.link}
      badgeBackgroundColor={colors.destructiveFill}
      backBehavior="initialRoute"
      hidden={tabBarHidden}
      {...Platform.select({
        android: {
          backgroundColor: colors.cardBackground,
          indicatorColor: colors.primaryMedium,
          iconColor: { default: colors.mutedForeground, selected: colors.foreground },
          labelStyle: {
            default: { color: colors.mutedForeground },
            selected: { color: colors.foreground, fontWeight: "600" },
          },
        },
        default: {},
      })}
    >
      <NativeTabs.Trigger name="home">
        <NativeTabs.Trigger.Icon sf="house" md="home" />
        <NativeTabs.Trigger.Label>{t("tabHome")}</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger name="boards">
        <NativeTabs.Trigger.Icon sf="square.grid.2x2" md="view_kanban" />
        <NativeTabs.Trigger.Label>{t("tabBoards")}</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger
        name="incidents/index"
        unstable_nativeProps={{ tabBarItemAccessibilityLabel: incidentsLabel }}
      >
        <NativeTabs.Trigger.Icon sf="bell.badge" md="e911_emergency" />
        <NativeTabs.Trigger.Label>{t("tabIncidents")}</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Badge hidden={!incidentBadge}>
          {incidentBadge ?? undefined}
        </NativeTabs.Trigger.Badge>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger
        name="chat"
        unstable_nativeProps={{ tabBarItemAccessibilityLabel: chatLabel }}
      >
        <NativeTabs.Trigger.Icon sf="bubble.left.and.bubble.right" md="chat" />
        <NativeTabs.Trigger.Label>{t("tabChat")}</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Badge hidden={!chatBadge}>
          {chatBadge ?? undefined}
        </NativeTabs.Trigger.Badge>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger
        name="account"
        unstable_nativeProps={{ tabBarItemAccessibilityLabel: accountLabel }}
      >
        <NativeTabs.Trigger.Icon sf="person.crop.circle" md="account_circle" />
        <NativeTabs.Trigger.Label>{t("tabAccount")}</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Badge hidden={!invitationBadge}>
          {invitationBadge ?? undefined}
        </NativeTabs.Trigger.Badge>
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}
