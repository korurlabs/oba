import appMessages from "./locales/en.json";

/// <reference types="@oba/shared/i18n.d.ts" />

type AppMessages = typeof appMessages;
// NOTE: This file should not be edited otherwise translation related types may not work

declare global {
  interface AppSpecificMessages extends AppMessages {}
}
