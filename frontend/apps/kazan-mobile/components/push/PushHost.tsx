import * as Notifications from "expo-notifications";
import { useGlobalSearchParams, usePathname, useRouter } from "expo-router";
import * as React from "react";
import { AppState, Platform } from "react-native";

import { pullRequestFilterCount } from "@/components/pull-requests/pullRequestModel";
import { usePullRequestFilter } from "@/components/pull-requests/pullRequestFilterStore";
import { PushBanner, type PushBannerItem } from "@/components/push/PushBanner";
import { toast } from "@/components/ui/toast";
import { useStoredUpdateFilter } from "@/components/updates/updateFilterStore";
import { resolveUpdateFilter, updateFilterCount } from "@/components/updates/updateModel";
import { useAuth } from "@/contexts/AuthContext";
import { useChatChannels } from "@/hooks/queries/chat";
import { registerPushDevice } from "@/hooks/queries/push";
import { useMe } from "@/hooks/queries/users";
import { useTranslation } from "@/i18n";
import { businessToday } from "@/lib/dates";
import { organizationAccessOf, type OrganizationAccess } from "@/lib/organizationAccess";
import {
  configurePush,
  decideForeground,
  getPushPermission,
  readPrePromptSeen,
  setOpenConversation,
  withdrawConversationNotifications,
  type PushPermission,
} from "@/lib/push";
import {
  openConversationFromRoute,
  parsePushData,
  pushHref,
  pushOrganizationStep,
  pushPermissionStep,
  pushSentAt,
  type OpenConversation,
  type PurposeChannelsOnScreen,
  type PushHref,
  type PushOrganizationStep,
} from "@/lib/pushModel";
import { ChatChannelPurpose, type ChatChannelResponse } from "@/lib/schemas/chat";
import type { PushData } from "@/lib/schemas/push";

const UPDATES_PATH = "/chat/updates";
const PULL_REQUESTS_PATH = "/chat/pull-requests";

/**
 * The daily updates and pull requests channels while their own screen is on
 * top and shows them whole: today with no person picked, and no filter. The
 * channel list is read only on those screens, which load it themselves.
 */
function usePurposeChannelsOnScreen(
  pathname: string,
  organizationId: string | null
): PurposeChannelsOnScreen {
  const onUpdates: boolean = pathname === UPDATES_PATH;
  const onPullRequests: boolean = pathname === PULL_REQUESTS_PATH;
  const channels: ChatChannelResponse[] | undefined = useChatChannels(
    onUpdates || onPullRequests
  ).data;
  const storedUpdateFilter = useStoredUpdateFilter(organizationId);
  const pullRequestFilter = usePullRequestFilter(organizationId);
  const today: string = businessToday();
  const updatesWhole: boolean =
    updateFilterCount(resolveUpdateFilter(storedUpdateFilter, today), today) === 0;
  const pullRequestsWhole: boolean = pullRequestFilterCount(pullRequestFilter) === 0;
  const channelOf = (purpose: ChatChannelPurpose): string | null =>
    channels?.find((channel) => channel.purpose === purpose)?.id ?? null;
  const dailyUpdates: string | null =
    onUpdates && updatesWhole ? channelOf(ChatChannelPurpose.DAILY_UPDATES) : null;
  const pullRequests: string | null =
    onPullRequests && pullRequestsWhole ? channelOf(ChatChannelPurpose.PULL_REQUESTS) : null;
  return React.useMemo(() => ({ dailyUpdates, pullRequests }), [dailyUpdates, pullRequests]);
}

/** How long the in-app banner stays before it slides away. */
const BANNER_DURATION_MS = 5000;

function messageOf(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

/**
 * Push for the signed-in app, mounted once inside the organization gate:
 *
 * - registers this phone on every start and after a token or language
 *   change, and shows the pre-prompt once when the system has never asked;
 * - opens the screen of a tapped notification, also the one that started the
 *   app, switching organization first when the push belongs to another one;
 * - keeps the open chat conversation known to the foreground handler, and
 *   removes that conversation's notifications from the shade; the daily
 *   updates and pull requests screens count while they show their channel
 *   whole;
 * - shows the in-app banner for a push that arrives while the app is open.
 */
export function PushHost() {
  if (Platform.OS === "web") return null;
  return <NativePushHost />;
}

function NativePushHost() {
  const { t, language } = useTranslation();
  const router = useRouter();
  const { activeOrganizationId, switchOrganization } = useAuth();
  const me = useMe(true);
  const organizationAccess: OrganizationAccess | null = React.useMemo(
    () => (me.data ? organizationAccessOf(me.data) : null),
    [me.data]
  );
  const [banner, setBanner] = React.useState<PushBannerItem | null>(null);

  // ── Setup and registration ────────────────────────────────────────

  React.useEffect(() => {
    configurePush().catch((error: unknown) => {
      // configurePush logged the cause; the user learns pushes may not arrive.
      console.warn("[push] push setup failed", messageOf(error));
      toast.error(t("pushSetupFailed"));
    });
  }, [t]);

  const reportedRegisterFailure = React.useRef<boolean>(false);
  const register = React.useCallback(
    (language: string) => {
      registerPushDevice(language).catch((error: unknown) => {
        console.warn("[push] registering this device failed", messageOf(error));
        // Once per start: a broken registration should not toast on every token event.
        if (reportedRegisterFailure.current) return;
        reportedRegisterFailure.current = true;
        toast.error(t("pushRegisterFailed"));
      });
    },
    [t]
  );

  const askedThisStart = React.useRef<boolean>(false);
  React.useEffect(() => {
    let cancelled = false;
    getPushPermission()
      .then((permission: PushPermission) => {
        if (cancelled) return;
        const step = pushPermissionStep({ ...permission, prePromptSeen: readPrePromptSeen() });
        if (step === "register") {
          register(language);
        } else if (step === "ask" && !askedThisStart.current) {
          askedThisStart.current = true;
          router.push("/notification-permission");
        }
      })
      .catch((error: unknown) => {
        console.warn("[push] reading the notification permission failed", messageOf(error));
        toast.error(t("pushSetupFailed"));
      });
    return () => {
      cancelled = true;
    };
  }, [language, register, router, t]);

  // A new token (reinstall, restore, rotation) replaces the old one on the server.
  React.useEffect(() => {
    const subscription = Notifications.addPushTokenListener(() => register(language));
    return () => subscription.remove();
  }, [language, register]);

  // ── The open conversation ─────────────────────────────────────────

  const pathname: string = usePathname();
  const params = useGlobalSearchParams<Record<string, string | string[]>>();
  const routeChannelId: string | undefined = Array.isArray(params.channelId)
    ? params.channelId[0]
    : params.channelId;
  const purposeChannels: PurposeChannelsOnScreen = usePurposeChannelsOnScreen(
    pathname,
    activeOrganizationId
  );
  const open: OpenConversation | null = React.useMemo(
    () =>
      openConversationFromRoute(
        pathname,
        { channelId: routeChannelId },
        activeOrganizationId,
        purposeChannels
      ),
    [pathname, routeChannelId, activeOrganizationId, purposeChannels]
  );
  React.useEffect(() => {
    setOpenConversation(open);
    // The conversation is on screen now, so its notifications are read.
    if (open && AppState.currentState === "active") {
      void withdrawConversationNotifications(open);
    }
  }, [open]);

  React.useEffect(() => () => setOpenConversation(null), []);

  // ── Tap routing ───────────────────────────────────────────────────

  const openPush = React.useCallback(
    async (data: PushData) => {
      const href: PushHref | null = pushHref(data);
      if (!href) return;
      const step: PushOrganizationStep = pushOrganizationStep(
        data,
        activeOrganizationId,
        organizationAccess
      );
      if (step === "not-member") {
        toast.info(t("pushOrganizationGone"));
        return;
      }
      if (step === "no-kazan") {
        toast.info(t("pushKazanClosed"));
        return;
      }
      if (step === "switch") {
        try {
          await switchOrganization(data.organizationId);
        } catch (error: unknown) {
          console.warn("[push] switching organization for a notification failed", messageOf(error));
          toast.error(t("orgSwitchFailed"));
          return;
        }
      }
      router.push(href);
    },
    [activeOrganizationId, organizationAccess, router, switchOrganization, t]
  );

  const lastResponse = Notifications.useLastNotificationResponse();
  const handledTap = React.useRef<string | null>(null);
  React.useEffect(() => {
    if (!lastResponse) return;
    if (lastResponse.actionIdentifier !== Notifications.DEFAULT_ACTION_IDENTIFIER) return;
    const key: string = lastResponse.notification.request.identifier;
    if (handledTap.current === key) return;
    handledTap.current = key;
    // Cleared, so signing in again does not replay the tap that started the app.
    Notifications.clearLastNotificationResponse();
    const data: PushData | null = parsePushData(lastResponse.notification.request.content.data);
    if (!data) {
      console.warn("[push] a tapped notification carries no Kazan push data");
      return;
    }
    openPush(data).catch((error: unknown) => {
      console.warn("[push] opening a tapped notification failed", messageOf(error));
      toast.error(t("pushOpenFailed"));
    });
  }, [lastResponse, openPush, t]);

  // ── Arrival while open ────────────────────────────────────────────

  React.useEffect(() => {
    const subscription = Notifications.addNotificationReceivedListener(
      (notification: Notifications.Notification) => {
        const data: PushData | null = parsePushData(notification.request.content.data);
        if (!data) return;
        const decision = decideForeground(data);
        if (decision === "withdraw") {
          void withdrawConversationNotifications(data);
          return;
        }
        if (decision === "silent") return;
        setBanner({
          id: notification.request.identifier,
          title: notification.request.content.title,
          body: notification.request.content.body,
          sentAt: pushSentAt(data, notification.date, Platform.OS),
          data,
        });
      }
    );
    return () => subscription.remove();
  }, []);

  React.useEffect(() => {
    if (!banner) return;
    const timer = setTimeout(() => setBanner(null), BANNER_DURATION_MS);
    return () => clearTimeout(timer);
  }, [banner]);

  const onOpenBanner = React.useCallback(
    (item: PushBannerItem) => {
      setBanner(null);
      // The banner stands for the notification in the list, which is read once opened.
      Notifications.dismissNotificationAsync(item.id).catch((error: unknown) => {
        console.warn("[push] removing an opened notification failed", messageOf(error));
      });
      openPush(item.data).catch((error: unknown) => {
        console.warn("[push] opening the in-app banner failed", messageOf(error));
        toast.error(t("pushOpenFailed"));
      });
    },
    [openPush, t]
  );

  return <PushBanner item={banner} onOpen={onOpenBanner} onDismiss={() => setBanner(null)} />;
}
