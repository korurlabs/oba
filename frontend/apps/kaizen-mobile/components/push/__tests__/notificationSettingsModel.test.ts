import { describe, expect, it } from "bun:test";

import {
  incidentsFooterKey,
  permissionJustGranted,
  permissionRow,
  permissionValueKey,
  withPreference,
  type PermissionState,
} from "@/components/push/notificationSettingsModel";
import type { PushPreferences } from "@/lib/schemas/push";

const GRANTED: PermissionState = { granted: true, undetermined: false };
const DENIED: PermissionState = { granted: false, undetermined: false };
const NEVER_ASKED: PermissionState = { granted: false, undetermined: true };

describe("permissionRow on iOS", () => {
  it("reads On and opens Settings when notifications are allowed", () => {
    expect(permissionRow("ios", GRANTED)).toEqual({
      titleKey: "notificationSettingsAllow",
      subtitleKey: "notificationSettingsInIosSettings",
      valueKey: "notificationSettingsOn",
      subtitleTone: "muted",
      action: "open-settings",
    });
  });

  it("reads Off and opens Settings once the user said no", () => {
    const row = permissionRow("ios", DENIED);
    expect(row.valueKey).toBe("notificationSettingsOff");
    expect(row.action).toBe("open-settings");
  });

  it("asks the system when the pre-prompt was skipped and it never asked", () => {
    const row = permissionRow("ios", NEVER_ASKED);
    expect(row.valueKey).toBe("notificationSettingsOff");
    expect(row.subtitleKey).toBe("notificationSettingsTapToTurnOn");
    expect(row.action).toBe("request");
  });
});

describe("permissionRow on Android", () => {
  it("opens the channels without a value when notifications are on", () => {
    expect(permissionRow("android", GRANTED)).toEqual({
      titleKey: "notificationSettingsSystem",
      subtitleKey: "notificationSettingsSystemSubtitle",
      valueKey: null,
      subtitleTone: "muted",
      action: "open-settings",
    });
  });

  it("warns when notifications are off", () => {
    const row = permissionRow("android", DENIED);
    expect(row.subtitleKey).toBe("notificationSettingsSystemOff");
    expect(row.subtitleTone).toBe("destructive");
    expect(row.action).toBe("open-settings");
  });

  it("asks the system on Android 13 and later when it never asked", () => {
    expect(permissionRow("android", NEVER_ASKED).action).toBe("request");
  });
});

describe("incidentsFooterKey", () => {
  it("names Time Sensitive on iOS and the high-importance channel on Android", () => {
    expect(incidentsFooterKey("ios")).toBe("notificationSettingsIncidentsFooterIos");
    expect(incidentsFooterKey("android")).toBe("notificationSettingsIncidentsFooterAndroid");
  });
});

describe("withPreference", () => {
  const all: PushPreferences = {
    mentionsAndReplies: true,
    everyone: true,
    here: true,
    urgentIncidents: true,
    showPreviews: true,
  };

  it("sends all five switches with only the flipped one changed", () => {
    expect(withPreference(all, "here", false)).toEqual({ ...all, here: false });
  });

  it("leaves the current set as it is", () => {
    withPreference(all, "showPreviews", false);
    expect(all.showPreviews).toBe(true);
  });
});

describe("permissionJustGranted", () => {
  it("is true when the permission turns on", () => {
    expect(permissionJustGranted(DENIED, GRANTED)).toBe(true);
    expect(permissionJustGranted(NEVER_ASKED, GRANTED)).toBe(true);
  });

  it("is false on the first read, which the start already registered", () => {
    expect(permissionJustGranted(null, GRANTED)).toBe(false);
  });

  it("is false when nothing changed or the permission turned off", () => {
    expect(permissionJustGranted(GRANTED, GRANTED)).toBe(false);
    expect(permissionJustGranted(GRANTED, DENIED)).toBe(false);
    expect(permissionJustGranted(NEVER_ASKED, DENIED)).toBe(false);
  });
});

describe("permissionValueKey", () => {
  it("reads On when notifications are allowed and Off otherwise", () => {
    expect(permissionValueKey(GRANTED)).toBe("notificationSettingsOn");
    expect(permissionValueKey(DENIED)).toBe("notificationSettingsOff");
    expect(permissionValueKey(NEVER_ASKED)).toBe("notificationSettingsOff");
  });

  it("shows nothing until the permission is read", () => {
    expect(permissionValueKey(null)).toBeNull();
  });
});
