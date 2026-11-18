/** Top-level keys under `userMe.userPreferences` shared across Oba apps. */
export const USER_PREFERENCE_THEME_KEY = "theme" as const;

export const USER_THEME_VALUES = ["light", "dark", "system"] as const;
export type UserThemePreferenceValue = (typeof USER_THEME_VALUES)[number];
