import type { PushPreferenceKey, PushPreferences } from "@/lib/schemas/push";

/*
 * The rules of Notification settings without native modules, so they run
 * under `bun test`: what the system permission row says and does, and the
 * set of switches a flip saves.
 */

/** The notification permission as the system reports it. */
export interface PermissionState {
  granted: boolean;
  /** Never asked: the system dialog can still be shown. */
  undetermined: boolean;
}

/**
 * What a tap on the permission row does: `request` shows the system dialog,
 * which the system allows only while it has never been answered; after that
 * only the system settings can change it.
 */
export type PermissionRowAction = "request" | "open-settings";

/** The permission row: iOS reads the state as On or Off; Android opens the channels. */
export interface PermissionRow {
  titleKey: "notificationSettingsAllow" | "notificationSettingsSystem";
  subtitleKey:
    | "notificationSettingsInIosSettings"
    | "notificationSettingsTapToTurnOn"
    | "notificationSettingsSystemSubtitle"
    | "notificationSettingsSystemOff";
  /** The trailing value; Android shows none, as its system screen holds the state. */
  valueKey: "notificationSettingsOn" | "notificationSettingsOff" | null;
  subtitleTone: "muted" | "destructive";
  action: PermissionRowAction;
}

/**
 * The On or Off on the Notifications row of Settings, the same on both
 * platforms; null until the permission is read, or when reading it failed.
 */
export function permissionValueKey(
  permission: PermissionState | null
): "notificationSettingsOn" | "notificationSettingsOff" | null {
  if (permission === null) return null;
  return permission.granted ? "notificationSettingsOn" : "notificationSettingsOff";
}

export function permissionRow(os: string, permission: PermissionState): PermissionRow {
  const action: PermissionRowAction = permission.undetermined ? "request" : "open-settings";
  if (os === "ios") {
    return {
      titleKey: "notificationSettingsAllow",
      subtitleKey: permission.undetermined
        ? "notificationSettingsTapToTurnOn"
        : "notificationSettingsInIosSettings",
      valueKey: permission.granted ? "notificationSettingsOn" : "notificationSettingsOff",
      subtitleTone: "muted",
      action,
    };
  }
  return {
    titleKey: "notificationSettingsSystem",
    subtitleKey: permission.granted
      ? "notificationSettingsSystemSubtitle"
      : "notificationSettingsSystemOff",
    valueKey: null,
    subtitleTone: permission.granted ? "muted" : "destructive",
    action,
  };
}

/** The footer under Urgent incidents: how each platform lets an incident through. */
export function incidentsFooterKey(
  os: string
): "notificationSettingsIncidentsFooterIos" | "notificationSettingsIncidentsFooterAndroid" {
  return os === "ios"
    ? "notificationSettingsIncidentsFooterIos"
    : "notificationSettingsIncidentsFooterAndroid";
}

/** The full set `PUT /push/preferences` takes after one switch is flipped. */
export function withPreference(
  current: PushPreferences,
  key: PushPreferenceKey,
  value: boolean
): PushPreferences {
  return { ...current, [key]: value };
}

/**
 * Whether the phone should register now: the permission was just given,
 * on this screen or in the system settings the user came back from.
 */
export function permissionJustGranted(
  previous: PermissionState | null,
  next: PermissionState
): boolean {
  return previous !== null && !previous.granted && next.granted;
}
