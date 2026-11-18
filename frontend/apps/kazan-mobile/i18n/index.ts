import { useCallback } from "react";
import { Platform } from "react-native";
import i18n from "i18next";
import { initReactI18next, useTranslation as useI18nextTranslation } from "react-i18next";
import { getLocales } from "expo-localization";
import * as SecureStore from "expo-secure-store";

import en from "./locales/en.json";
import nl from "./locales/nl.json";
import tr from "./locales/tr.json";

/**
 * The base name of a plural key: en.json holds `x_one` and `x_other`, and
 * `t("x", { count })` picks the form (`compatibilityJSON: "v4"`; en, nl and tr
 * each have exactly the CLDR categories one and other).
 */
type PluralBaseKey<Key> = Key extends `${infer Base}_one` ? Base : never;

/** The keys of the English resource, plus plural base names. */
export type TranslationKey = keyof typeof en | PluralBaseKey<keyof typeof en>;

const DEFAULT_LANGUAGE = "en";

/** The languages the app ships translations for, the same three as Kazan web. */
export const SUPPORTED_LANGUAGES = ["en", "nl", "tr"] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

function isSupportedLanguage(value: string): value is SupportedLanguage {
  return (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}

/**
 * The language of the device locale when the app ships it, English otherwise.
 * It seeds i18n at start when the user has not chosen a language (`readStoredLanguage`).
 */
export function getInitialLanguage(): SupportedLanguage {
  try {
    const deviceLanguage: string = getLocales()[0]?.languageCode ?? DEFAULT_LANGUAGE;
    if (isSupportedLanguage(deviceLanguage)) {
      return deviceLanguage;
    }
  } catch (error: unknown) {
    // A broken native localization module must not stop the app: it starts in
    // English, and the failure is logged.
    console.warn("[i18n] getLocales failed", error instanceof Error ? error.message : error);
  }
  return DEFAULT_LANGUAGE;
}

/** SecureStore key of the language the user chose on Sign in or in Settings. */
export const LANGUAGE_STORAGE_KEY = "kazan_language";

/**
 * The language the user chose earlier, or null when none was chosen. It is
 * read synchronously at start, so the first screen is already in that
 * language. SecureStore is the app's only device store; the value is not
 * secret. A failed read starts the app in the device language and is logged.
 */
export function readStoredLanguage(): SupportedLanguage | null {
  if (Platform.OS === "web") return null;
  try {
    const stored: string | null = SecureStore.getItem(LANGUAGE_STORAGE_KEY);
    return stored && isSupportedLanguage(stored) ? stored : null;
  } catch (error: unknown) {
    console.warn(
      "[i18n] reading the stored language failed",
      error instanceof Error ? error.message : error
    );
    return null;
  }
}

/**
 * Keeps the language the user switched to, so the next start opens in it.
 * The switch itself has already happened; a failed write is logged and the
 * next start falls back to the device language.
 */
function persistLanguage(language: string): void {
  if (Platform.OS === "web" || !isSupportedLanguage(language)) return;
  SecureStore.setItemAsync(LANGUAGE_STORAGE_KEY, language).catch((error: unknown) => {
    console.warn(
      "[i18n] saving the language failed",
      error instanceof Error ? error.message : error
    );
  });
}

i18n
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      nl: { translation: nl },
      tr: { translation: tr },
    },
    lng: readStoredLanguage() ?? getInitialLanguage(),
    fallbackLng: DEFAULT_LANGUAGE,
    interpolation: { escapeValue: false },
    compatibilityJSON: "v4",
  })
  .then(() => {
    // Listening only after init keeps the device language from being stored
    // as a choice: every later switch (the Sign in pill, Settings) is one.
    i18n.on("languageChanged", persistLanguage);
  })
  .catch((error: unknown) => {
    console.error("[i18n] init failed", error instanceof Error ? error.message : error);
  });

/**
 * The app's translation hook: react-i18next's `useTranslation` with the typed
 * key set. It keeps the react-i18next subscription, so a language switch
 * re-renders.
 */
export function useTranslation() {
  const { t: rawT, i18n: instance } = useI18nextTranslation();
  const t = useCallback(
    (key: TranslationKey, options?: Record<string, unknown>): string =>
      rawT(key, options as never) as unknown as string,
    [rawT]
  );
  return { t, language: instance.language };
}

/** The typed translator for code outside React render, such as mutation callbacks. */
export function translate(key: TranslationKey, options?: Record<string, unknown>): string {
  return i18n.t(key, options as never) as unknown as string;
}

export default i18n;
