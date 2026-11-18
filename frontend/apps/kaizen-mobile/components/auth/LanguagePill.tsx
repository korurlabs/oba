import { useState } from "react";
import { Platform, Pressable } from "react-native";
import { Globe } from "lucide-react-native";

import { ActionSheet } from "@/components/kaizen/ActionSheet";
import { toast } from "@/components/ui/toast";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import i18n, { SUPPORTED_LANGUAGES, useTranslation, type SupportedLanguage } from "@/i18n";

/** Each language in its own name, so a reader finds theirs whatever the current language is. */
export const LANGUAGE_NAMES: Record<SupportedLanguage, string> = {
  en: "English",
  nl: "Nederlands",
  tr: "Türkçe",
};

function languageName(language: string): string {
  return (SUPPORTED_LANGUAGES as readonly string[]).includes(language)
    ? LANGUAGE_NAMES[language as SupportedLanguage]
    : LANGUAGE_NAMES.en;
}

/**
 * The language choice before sign-in. It switches the app's language, and the
 * Keycloak login page follows it, because sign-in sends the current language
 * as `ui_locales`.
 */
export function LanguagePill() {
  const { t, language } = useTranslation();
  const [open, setOpen] = useState(false);

  const choose = (next: SupportedLanguage) => {
    i18n.changeLanguage(next).catch((error: unknown) => {
      console.warn(
        "[signIn] changing the language failed",
        error instanceof Error ? error.message : error
      );
      toast.error(t("authLanguageChangeFailed"));
    });
  };

  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={t("authLanguageLabel", { language: languageName(language) })}
        // Grows the 32 pt pill to 44 pt on iOS and 48 dp on Android.
        hitSlop={Platform.OS === "android" ? 8 : 6}
        onPress={() => setOpen(true)}
        className="min-h-8 flex-row items-center gap-1.5 rounded-full border border-border bg-card px-3 py-1 active:opacity-60"
      >
        <Icon as={Globe} size={16} className="text-foreground" />
        <Text className="font-sans-medium text-subhead text-foreground">
          {languageName(language)}
        </Text>
      </Pressable>
      <ActionSheet
        visible={open}
        onClose={() => setOpen(false)}
        title={t("authLanguageTitle")}
        cancelLabel={t("kitCancel")}
        actions={SUPPORTED_LANGUAGES.map((code) => ({
          label: LANGUAGE_NAMES[code],
          selected: code === language,
          onPress: () => {
            if (code !== language) choose(code);
          },
        }))}
      />
    </>
  );
}
