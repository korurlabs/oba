import { redirect } from "next/navigation";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";

/**
 * The general settings (language, appearance) are kept in the account app,
 * which every Oba app shares, so an old or bookmarked Kazan link lands
 * there. The Kazan-only settings stay under `/settings/columns` and
 * `/settings/alerts`. Account is another origin, so the plain Next.js
 * redirect is used rather than the locale-prefixing one.
 */
export default function SettingsPage(): never {
  redirect(ACCOUNT_LINKS.settings);
}
