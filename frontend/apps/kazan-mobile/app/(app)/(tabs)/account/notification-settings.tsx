import { AtSign, Bell, Eye, Megaphone, Radio, Siren } from "lucide-react-native";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Platform, RefreshControl, ScrollView } from "react-native";

import { EmptyState } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import {
  incidentsFooterKey,
  permissionJustGranted,
  permissionRow,
  withPreference,
  type PermissionRow,
} from "@/components/push/notificationSettingsModel";
import { usePushPermission } from "@/components/push/usePushPermission";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import {
  usePushPreferences,
  useRegisterPushDevice,
  useUpdatePushPreferences,
} from "@/hooks/queries/push";
import { useTranslation } from "@/i18n";
import {
  markPrePromptSeen,
  openNotificationSettings,
  requestPushPermission,
  type PushPermission,
} from "@/lib/push";
import type { PushPreferenceKey, PushPreferences } from "@/lib/schemas/push";

function messageOf(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

/**
 * Notification settings: whether the system lets Kazan notify on this
 * phone, and the five switches the server applies to every phone of the
 * user. A switch saves at once and flips back when the save fails.
 *
 * When the pre-prompt was skipped, the permission row asks the system here;
 * once the system has an answer, only its settings can change it, so the row
 * opens them. A permission given here or in the system settings registers
 * this phone at once, without waiting for the next start.
 */
export default function NotificationSettingsScreen() {
  const { t, language } = useTranslation();
  const colors = useThemeColors();
  const preferences = usePushPreferences();
  const update = useUpdatePushPreferences();
  const register = useRegisterPushDevice();
  const { permission, failed: permissionFailed, refresh: refreshPermission } = usePushPermission();
  const [asking, setAsking] = React.useState<boolean>(false);
  const [refreshing, setRefreshing] = React.useState<boolean>(false);

  // ── Permission ────────────────────────────────────────────────────

  const registerMutate = register.mutateAsync;
  const previousPermission = React.useRef<PushPermission | null>(null);
  React.useEffect(() => {
    if (!permission) return;
    const granted: boolean = permissionJustGranted(previousPermission.current, permission);
    previousPermission.current = permission;
    if (!granted) return;
    registerMutate(language)
      .then(() => toast.success(t("pushTurnedOn")))
      .catch((error: unknown) => {
        // The hook logged the cause; the next start registers again.
        console.warn(
          "[push] notification settings could not register this device",
          messageOf(error)
        );
        toast.error(t("pushRegisterFailed"));
      });
  }, [permission, registerMutate, language, t]);

  const turnOn = async () => {
    setAsking(true);
    try {
      await markPrePromptSeen();
    } catch (error: unknown) {
      // The pre-prompt may show once more at the next start; asking goes on.
      console.warn("[push] remembering the pre-prompt answer failed", messageOf(error));
    }
    try {
      const answer: PushPermission = await requestPushPermission();
      if (!answer.granted) toast.info(t("pushPermissionDenied"));
    } catch (error: unknown) {
      console.warn("[push] asking for the notification permission failed", messageOf(error));
      toast.error(t("pushPermissionFailed"));
    }
    // The effect above registers the phone when the new state is granted.
    await refreshPermission();
    setAsking(false);
  };

  const openSystemSettings = () => {
    openNotificationSettings().catch((error: unknown) => {
      console.warn("[push] opening the system settings failed", messageOf(error));
      toast.error(t("notificationSettingsOpenFailed"));
    });
  };

  const row: PermissionRow | null = permission ? permissionRow(Platform.OS, permission) : null;
  const onPermissionPress = () => {
    if (row?.action === "request") void turnOn();
    else openSystemSettings();
  };
  const fallbackTitleKey =
    Platform.OS === "ios" ? "notificationSettingsAllow" : "notificationSettingsSystem";

  // ── Switches ──────────────────────────────────────────────────────

  const save = (key: PushPreferenceKey, value: boolean) => {
    const current: PushPreferences | undefined = preferences.data;
    if (!current) return;
    update.mutate(withPreference(current, key, value), {
      onError: () => toast.error(t("notificationSettingsSaveFailed")),
    });
  };

  const switchRow = (
    key: PushPreferenceKey,
    title: string,
    icon: LucideIcon,
    iconTone?: string,
    subtitle?: string
  ) => (
    <ListRow
      key={key}
      title={title}
      subtitle={subtitle}
      icon={icon}
      iconTone={iconTone}
      accessory="switch"
      checked={preferences.data?.[key] ?? false}
      onCheckedChange={(value: boolean) => save(key, value)}
    />
  );

  const refresh = React.useCallback(() => {
    setRefreshing(true);
    Promise.all([preferences.refetch(), refreshPermission()])
      .catch((error: unknown) => {
        // The error state below shows a failed read; this only records the pull.
        console.warn("[push] refreshing notification settings failed", messageOf(error));
      })
      .finally(() => setRefreshing(false));
  }, [preferences, refreshPermission]);

  const switches = preferences.isPending ? (
    <SkeletonGroup className="mx-4 mt-6 gap-4">
      <SkeletonRow />
      <SkeletonRow />
      <SkeletonRow />
    </SkeletonGroup>
  ) : preferences.isError ? (
    <EmptyState
      variant="error"
      title={t("notificationSettingsLoadFailed")}
      onAction={() => void preferences.refetch()}
      className="mt-8"
    />
  ) : (
    <>
      <ListSection
        header={t("notificationSettingsChat")}
        footer={t("notificationSettingsChatFooter")}
      >
        {switchRow(
          "mentionsAndReplies",
          t("pushChannelMentionsReplies"),
          AtSign,
          undefined,
          t("notificationSettingsMentionsSubtitle")
        )}
        {switchRow("everyone", t("notificationSettingsEveryone"), Megaphone)}
        {switchRow(
          "here",
          t("notificationSettingsHere"),
          Radio,
          undefined,
          t("notificationSettingsHereSubtitle")
        )}
      </ListSection>
      <ListSection
        header={t("notificationSettingsIncidents")}
        footer={t(incidentsFooterKey(Platform.OS))}
      >
        {switchRow(
          "urgentIncidents",
          t("pushChannelUrgentIncidents"),
          Siren,
          "bg-destructive",
          t("notificationSettingsIncidentsSubtitle")
        )}
      </ListSection>
      <ListSection
        header={t("notificationSettingsPrivacy")}
        footer={t("notificationSettingsPreviewsFooter")}
      >
        {switchRow("showPreviews", t("notificationSettingsPreviews"), Eye, "bg-muted-foreground")}
      </ListSection>
    </>
  );

  return (
    <ScrollView
      contentInsetAdjustmentBehavior="automatic"
      contentContainerClassName="pb-10"
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={refresh}
          tintColor={colors.mutedForeground}
        />
      }
    >
      <ListSection footer={t("notificationSettingsDeviceFooter")}>
        <ListRow
          title={t(row?.titleKey ?? fallbackTitleKey)}
          subtitle={
            row
              ? t(row.subtitleKey)
              : permissionFailed
                ? t("notificationSettingsPermissionUnknown")
                : undefined
          }
          subtitleTone={row?.subtitleTone ?? "muted"}
          value={row?.valueKey ? t(row.valueKey) : undefined}
          icon={Bell}
          iconTone="bg-destructive"
          disabled={asking || register.isPending}
          onPress={onPermissionPress}
        />
      </ListSection>
      {switches}
    </ScrollView>
  );
}
