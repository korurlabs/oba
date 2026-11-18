import { useIsFocused } from "@react-navigation/native";
import * as React from "react";

import {
  cappedRoleRead,
  ROLE_MENU_WAIT_MS,
  roleMenuLacksRole,
  roleMenuVisible,
  type RoleReadState,
} from "@/components/kaizen/actionSheetModel";
import { toast } from "@/components/ui/toast";
import { useTranslation } from "@/i18n";

type RoleGatedMenuInput = {
  /** True once the user asked for the menu, until it closes. */
  requested: boolean;
  /** The read of the role the menu's actions depend on. */
  role: RoleReadState & { refetch: () => void };
  /** Drops the request, as closing the menu does. */
  onCancel: () => void;
  /** The screen's log tag, such as "boards". */
  logTag: string;
};

/**
 * The `visible` of an ActionSheet whose actions depend on the reader's role
 * (see `roleMenuVisible`). A menu waits for the role at most
 * `ROLE_MENU_WAIT_MS`; one that opens without the role, because its read
 * failed, stalls or is too slow, says so in a toast and reads a failed role
 * again for the next opening. A menu still waiting for the role when its
 * screen loses focus is dropped, so it never pops up over the screen the user
 * went to.
 */
export function useRoleGatedMenu({
  requested,
  role,
  onCancel,
  logTag,
}: RoleGatedMenuInput): boolean {
  const { t } = useTranslation();
  const focused: boolean = useIsFocused();
  const [shown, setShown] = React.useState<boolean>(false);
  const [waitExpired, setWaitExpired] = React.useState<boolean>(false);
  const read = cappedRoleRead(role, waitExpired);
  const visible: boolean = roleMenuVisible(requested, shown, read);
  const waiting: boolean = requested && !visible;
  // The latest inputs, read when the menu opens or the screen blurs, without
  // running those effects again on every render or on a language change.
  const latest = React.useRef({ read, onCancel, logTag, t });
  latest.current = { read, onCancel, logTag, t };

  React.useEffect(() => {
    if (!waiting) return;
    const timer: ReturnType<typeof setTimeout> = setTimeout(
      () => setWaitExpired(true),
      ROLE_MENU_WAIT_MS
    );
    return () => clearTimeout(timer);
  }, [waiting]);

  React.useEffect(() => {
    if (!requested) setWaitExpired(false);
  }, [requested]);

  React.useEffect(() => {
    setShown(visible);
    if (!visible) return;
    const current = latest.current;
    if (!roleMenuLacksRole(current.read)) return;
    const reason: string = current.read.error?.message ?? "the read is offline, retrying or slow";
    console.warn(`[${current.logTag}] menu opened without the board role`, reason);
    toast.warning(current.t("boardRoleUnavailable"));
    current.read.refetch();
  }, [visible]);

  React.useEffect(() => {
    if (!focused && requested) latest.current.onCancel();
  }, [focused, requested]);

  return visible;
}
