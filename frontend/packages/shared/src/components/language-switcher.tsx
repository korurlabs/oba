"use client";

import { Globe } from "lucide-react";
import { useLocale } from "next-intl";
import { useRouter } from "next/navigation";
import * as React from "react";

import { localeCookieString } from "@oba/shared/libs/i18n/locale-cookie";
import { extractLocale, stripLocale } from "@oba/shared/libs/i18n/locale-prefix";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@oba/ui/components/dropdown-menu";
import { cn } from "@oba/ui/lib/utils";

export interface Language {
  code: string;
  name: string;
  nativeName: string;
  flag?: string;
}

export const languages: Language[] = [
  { code: "en", name: "English", nativeName: "English", flag: "GB" },
  { code: "nl", name: "Dutch", nativeName: "Nederlands", flag: "NL" },
  { code: "de", name: "German", nativeName: "Deutsch", flag: "DE" },
  { code: "tr", name: "Turkish", nativeName: "Türkçe", flag: "TR" },
  { code: "es", name: "Spanish", nativeName: "Español", flag: "ES" },
];

/**
 * The locale list + switch handler shared by every presentation of the
 * language switcher (the standalone dropdown below and the
 * `LanguageSwitcherSubMenu` that nests inside another menu). Keeping it in one
 * hook is what guarantees both surfaces switch identically.
 *
 * A switch stores the choice in the locale cookie and re-renders the current
 * page on the server, where the proxy reads the cookie. The address stays the
 * same, path and query included, because Oba URLs carry no locale prefix.
 * next-intl's `router.replace(href, { locale })` is not used: it requests the
 * prefixed `/<locale>/...` path after it has already written the cookie, and
 * the proxy serves such a path through, prefix and all, when its locale is the
 * one in the cookie (see `runIntlMiddleware` in `proxy.ts`).
 *
 * An address that still carries a prefix (a bookmark or history entry from
 * older builds, or a typed one) is replaced by the same address without it.
 * Refreshing it would request the prefix against the new cookie, and the proxy
 * answers that with a redirect that writes the prefix's locale back into the
 * cookie, so the switch would be undone.
 */
export function useLanguageSwitch(supportedLocales?: string[]) {
  const locale = useLocale();
  const router = useRouter();

  const availableLanguages = supportedLocales
    ? languages.filter((lang) => supportedLocales.includes(lang.code))
    : languages;

  const currentLanguage =
    availableLanguages.find((lang) => lang.code === locale) || availableLanguages[0];

  const handleLanguageChange = (languageCode: string) => {
    if (languageCode === locale) return;
    // The page's own origin is the app's public origin, which sets the cookie domain.
    document.cookie = localeCookieString(languageCode, window.location.origin);
    const { pathname, search } = window.location;
    if (extractLocale(pathname)) {
      router.replace(stripLocale(pathname) + search);
      return;
    }
    // Also drops the client router cache, so pages visited in the old language
    // are fetched again in the new one.
    router.refresh();
  };

  return { locale, availableLanguages, currentLanguage, handleLanguageChange };
}

interface LanguageSwitcherProps {
  className?: string;
  /** "default" shows the language name, "minimal" is the compact header globe. */
  variant?: "default" | "minimal";
  supportedLocales?: string[];
}

export function LanguageSwitcher({
  className,
  variant = "default",
  supportedLocales,
}: LanguageSwitcherProps) {
  const { locale, availableLanguages, currentLanguage, handleLanguageChange } =
    useLanguageSwitch(supportedLocales);

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger
        aria-label="Select language"
        className={cn(
          "flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
          "hover:bg-muted focus-visible:ring-primary focus:outline-none focus-visible:ring-2",
          variant === "minimal" && "px-2 py-1.5",
          className
        )}
      >
        <Globe className="h-4 w-4" />
        {variant === "default" && (
          <span className="hidden sm:inline">{currentLanguage?.nativeName}</span>
        )}
        <span className="text-xs font-bold uppercase sm:hidden">{currentLanguage?.code}</span>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        side="bottom"
        className="min-w-[160px]"
      >
        {availableLanguages.map((language) => (
          <DropdownMenuItem
            key={language.code}
            onClick={() => handleLanguageChange(language.code)}
            className={cn(
              "flex cursor-pointer items-center gap-3",
              locale === language.code && "bg-primary/10 text-primary"
            )}
          >
            <span className="text-base">{getFlagEmoji(language.flag)}</span>
            <span className="flex-1">{language.nativeName}</span>
            {locale === language.code && <span className="bg-primary h-2 w-2 rounded-full" />}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

// Helper function to convert country code to flag emoji
function getFlagEmoji(countryCode?: string): string {
  if (!countryCode) return "";
  const codePoints = countryCode
    .toUpperCase()
    .split("")
    .map((char) => 127397 + char.charCodeAt(0));
  return String.fromCodePoint(...codePoints);
}

interface LanguageSwitcherSubMenuProps {
  /** Restricts the list to the locales the host app ships messages for. */
  supportedLocales?: string[];
  /** Label for the submenu trigger row, e.g. "Language". */
  label: string;
}

/**
 * The same locale list as `LanguageSwitcher`, presented as a submenu row so it
 * can live INSIDE another dropdown (Kaizen nests it in the sidebar account
 * menu). It renders no menu root of its own -- it must be a child of a
 * `DropdownMenuContent`.
 *
 * Routing is the shared `useLanguageSwitch` hook, so switching here behaves
 * exactly as it does from the standalone switcher.
 */
export function LanguageSwitcherSubMenu({ supportedLocales, label }: LanguageSwitcherSubMenuProps) {
  const { locale, availableLanguages, currentLanguage, handleLanguageChange } =
    useLanguageSwitch(supportedLocales);

  return (
    <DropdownMenuSub>
      <DropdownMenuSubTrigger className="cursor-pointer rounded-lg px-3 py-2.5">
        <Globe className="text-muted-foreground h-4 w-4" />
        <span className="flex-1">{label}</span>
        <span className="text-muted-foreground text-xs">{currentLanguage?.nativeName}</span>
      </DropdownMenuSubTrigger>
      <DropdownMenuSubContent
        // The host menu can sit at the very bottom of the viewport (Kaizen opens this from
        // the sidebar footer, so the trigger row is always ~110px above the window edge).
        // Radix already shifts a submenu up to keep it on screen, but with the default
        // collisionPadding of 0 it lands flush against the window edge and hangs below the
        // parent menu. Reserving the same 16px inset the parent menu keeps makes it flip
        // cleanly upward instead, bottom-aligned with the menu it belongs to.
        collisionPadding={16}
        className="min-w-[180px]"
      >
        {availableLanguages.map((language) => (
          <DropdownMenuItem
            key={language.code}
            onClick={() => handleLanguageChange(language.code)}
            className={cn(
              "flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5",
              locale === language.code && "bg-primary/10 text-primary"
            )}
          >
            <span className="text-base">{getFlagEmoji(language.flag)}</span>
            <span className="flex-1">{language.nativeName}</span>
            {locale === language.code && <span className="bg-primary h-2 w-2 rounded-full" />}
          </DropdownMenuItem>
        ))}
      </DropdownMenuSubContent>
    </DropdownMenuSub>
  );
}
