import sharedLang from "../../locales/en.json";
import type { Locale } from "./available-languages";

type SharedMessages = typeof sharedLang;

// Allow apps to extend this interface with their own messages
declare global {
  interface AppSpecificMessages {}
}

type Messages = SharedMessages & AppSpecificMessages;

declare module "next-intl" {
  interface AppConfig {
    Messages: Messages;
    Locale: Locale;
  }
}
