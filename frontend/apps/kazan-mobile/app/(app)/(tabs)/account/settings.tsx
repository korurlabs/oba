import { useRouter } from "expo-router";
import { Bell, Globe, Moon, Trash2 } from "lucide-react-native";
import * as React from "react";
import { Platform, ScrollView } from "react-native";

import {
  ACCOUNT_DELETION_OFFERED,
  APPEARANCES,
  type Appearance,
} from "@/components/account/accountModel";
import { useAccountActions } from "@/components/account/useAccountActions";
import { LANGUAGE_NAMES } from "@/components/auth/LanguagePill";
import { ActionSheet, type ActionSheetAction } from "@/components/kazan/ActionSheet";
import { ListRow, ListSection } from "@/components/kazan/List";
import { permissionValueKey } from "@/components/push/notificationSettingsModel";
import { usePushPermission } from "@/components/push/usePushPermission";
import { toast } from "@/components/ui/toast";
import { setAppearance, useAppearance } from "@/hooks/useAppearance";
import i18n, { SUPPORTED_LANGUAGES, useTranslation, type SupportedLanguage } from "@/i18n";

const IS_IOS = Platform.OS === "ios";

const APPEARANCE_LABEL_KEY = {
  system: "settingsAppearanceSystem",
  light: "settingsAppearanceLight",
  dark: "settingsAppearanceDark",
} as const satisfies Record<Appearance, string>;

type OpenSheet = "language" | "appearance" | null;

/**
 * Settings: the language and appearance of this phone, notification settings,
 * and deleting the account, which shows dimmed while account deletion is not
 * offered. Language and appearance are device preferences, kept on the phone
 * and applied at once.
 */
export default function SettingsScreen() {
  const { t, language } = useTranslation();
  const router = useRouter();
  const appearance: Appearance = useAppearance();
  const { confirmDeleteAccount, deletingAccount } = useAccountActions();
  // Read again on every return to the app, as the user changes it in the system settings.
  const { permission } = usePushPermission();
  const notificationsValue = permissionValueKey(permission);
  const [sheet, setSheet] = React.useState<OpenSheet>(null);

  const languageName: string = LANGUAGE_NAMES[language as SupportedLanguage] ?? LANGUAGE_NAMES.en;

  const chooseLanguage = (next: SupportedLanguage) => {
    i18n.changeLanguage(next).catch((error: unknown) => {
      console.warn(
        "[settings] changing the language failed",
        error instanceof Error ? error.message : error
      );
      toast.error(t("authLanguageChangeFailed"));
    });
  };

  const chooseAppearance = (next: Appearance) => {
    setAppearance(next).catch((error: unknown) => {
      // setAppearance logged the cause; the scheme is already on screen.
      console.warn(
        "[settings] keeping the appearance failed",
        error instanceof Error ? error.message : error
      );
      toast.warning(t("settingsAppearanceSaveFailed"));
    });
  };

  const languageActions: ActionSheetAction[] = SUPPORTED_LANGUAGES.map((option) => ({
    label: LANGUAGE_NAMES[option],
    selected: option === language,
    onPress: () => {
      if (option !== language) chooseLanguage(option);
    },
  }));

  const appearanceActions: ActionSheetAction[] = APPEARANCES.map((option) => ({
    label: t(APPEARANCE_LABEL_KEY[option]),
    selected: option === appearance,
    onPress: () => {
      if (option !== appearance) chooseAppearance(option);
    },
  }));

  return (
    <>
      <ScrollView contentInsetAdjustmentBehavior="automatic" contentContainerClassName="pb-10">
        <ListSection header={t("settingsGeneral")}>
          <ListRow
            title={t("settingsLanguage")}
            icon={Globe}
            iconTone="bg-success"
            value={languageName}
            onPress={() => setSheet("language")}
          />
          <ListRow
            title={t("settingsAppearance")}
            icon={Moon}
            iconTone="bg-purple"
            value={t(APPEARANCE_LABEL_KEY[appearance])}
            onPress={() => setSheet("appearance")}
          />
        </ListSection>
        <ListSection header={t("settingsNotifications")} footer={t("settingsNotificationsFooter")}>
          <ListRow
            title={t("settingsNotifications")}
            icon={Bell}
            iconTone="bg-destructive"
            value={notificationsValue ? t(notificationsValue) : undefined}
            onPress={() => router.push("/account/notification-settings")}
          />
        </ListSection>
        <ListSection
          footer={ACCOUNT_DELETION_OFFERED ? t("settingsDeleteAccountFooter") : undefined}
        >
          <ListRow
            title={t("settingsDeleteAccount")}
            subtitle={ACCOUNT_DELETION_OFFERED ? undefined : t("settingsDeleteAccountUnavailable")}
            icon={IS_IOS ? undefined : Trash2}
            center={IS_IOS}
            destructive
            disabled={!ACCOUNT_DELETION_OFFERED || deletingAccount}
            accessory="none"
            onPress={confirmDeleteAccount}
          />
        </ListSection>
      </ScrollView>
      <ActionSheet
        visible={sheet === "language"}
        onClose={() => setSheet(null)}
        title={t("authLanguageTitle")}
        actions={languageActions}
        cancelLabel={t("kitCancel")}
      />
      <ActionSheet
        visible={sheet === "appearance"}
        onClose={() => setSheet(null)}
        title={t("settingsAppearance")}
        actions={appearanceActions}
        cancelLabel={t("kitCancel")}
      />
    </>
  );
}
