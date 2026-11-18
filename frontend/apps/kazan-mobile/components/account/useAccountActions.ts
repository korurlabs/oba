import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { Alert, Platform } from "react-native";

import { SOLE_OWNER_ERROR_CODE } from "@/components/account/accountModel";
import { toast } from "@/components/ui/toast";
import { useAuth, type SignOutOutcome } from "@/contexts/AuthContext";
import { signOutWarningKey } from "@/contexts/signOutModel";
import { useDeleteAccount, userKeys } from "@/hooks/queries/users";
import { useTranslation } from "@/i18n";
import { isApiError } from "@/lib/api";
import {
  kazanWebUrl,
  openInAppBrowser,
  openKeycloakAccount,
  openPlatformPage as openPlatformPageInBrowser,
  type KeycloakAccountPage,
  type PlatformPage,
} from "@/lib/browser";

function messageOf(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

/**
 * The commands of the Account screens. Pages that change the account outside
 * the app (the Account and Admin web apps, the Keycloak account console) open in the in-app browser,
 * and the profile is read again when the user closes them, so invitations
 * and organizations accepted there show at once.
 */
export function useAccountActions() {
  const { t, language } = useTranslation();
  const queryClient = useQueryClient();
  const { signOut } = useAuth();
  const deleteAccount = useDeleteAccount();

  const refetchMe = useCallback((): Promise<void> => {
    return queryClient.invalidateQueries({ queryKey: userKeys.me });
  }, [queryClient]);

  /** Opens `open()` and reads the profile again once the browser closes. */
  const runBrowser = useCallback(
    (open: () => Promise<void>): void => {
      open()
        .then(refetchMe)
        .catch((error: unknown) => {
          console.warn("[account] opening the page failed", messageOf(error));
          toast.error(t("accountOpenFailed"));
        });
    },
    [refetchMe, t]
  );

  const openPlatformPage = useCallback(
    (page: PlatformPage) => runBrowser(() => openPlatformPageInBrowser(page)),
    [runBrowser]
  );

  const openAccountConsole = useCallback(
    (page: KeycloakAccountPage) => runBrowser(() => openKeycloakAccount(page)),
    [runBrowser]
  );

  const openKazanWeb = useCallback(() => {
    openInAppBrowser(kazanWebUrl(`/${language}/home`)).catch((error: unknown) => {
      console.warn("[account] opening Kazan web failed", messageOf(error));
      toast.error(t("accountOpenFailed"));
    });
  }, [language, t]);

  const runSignOut = useCallback((): Promise<void> => {
    return signOut().then(
      (outcome: SignOutOutcome) => {
        const warning = signOutWarningKey(outcome);
        if (warning) toast.warning(t(warning));
      },
      (error: unknown) => {
        console.warn("[account] sign-out incomplete", messageOf(error));
        toast.error(t("authSignOutIncomplete"));
      }
    );
  }, [signOut, t]);

  /** Asks before signing out; signing out also stops this phone's push alerts. */
  const confirmSignOut = useCallback(() => {
    Alert.alert(
      t("accountSignOutTitle"),
      t(Platform.OS === "ios" ? "accountSignOutBodyIos" : "accountSignOutBodyAndroid"),
      [
        { text: t("kitCancel"), style: "cancel" },
        { text: t("authSignOut"), style: "destructive", onPress: () => void runSignOut() },
      ]
    );
  }, [runSignOut, t]);

  const runDeleteAccount = useCallback(() => {
    deleteAccount.mutate(undefined, {
      onSuccess: () => {
        toast.success(t("deleteAccountDone"));
        void runSignOut();
      },
      onError: (error: Error) => {
        // The request is silent, so this is the only message the user sees.
        if (isApiError(error, SOLE_OWNER_ERROR_CODE)) {
          Alert.alert(t("deleteAccountSoleOwnerTitle"), t("deleteAccountSoleOwnerBody"), [
            { text: t("kitCancel"), style: "cancel" },
            { text: t("deleteAccountOpenAdmin"), onPress: () => openPlatformPage("organizations") },
          ]);
          return;
        }
        toast.error(t("deleteAccountFailed"));
      },
    });
  }, [deleteAccount, openPlatformPage, runSignOut, t]);

  /** Asks before disabling the account in every Korur app. */
  const confirmDeleteAccount = useCallback(() => {
    Alert.alert(t("deleteAccountTitle"), t("deleteAccountBody"), [
      { text: t("kitCancel"), style: "cancel" },
      { text: t("deleteAccountConfirm"), style: "destructive", onPress: runDeleteAccount },
    ]);
  }, [runDeleteAccount, t]);

  return {
    openPlatformPage,
    openAccountConsole,
    openKazanWeb,
    confirmSignOut,
    confirmDeleteAccount,
    deletingAccount: deleteAccount.isPending,
  };
}
