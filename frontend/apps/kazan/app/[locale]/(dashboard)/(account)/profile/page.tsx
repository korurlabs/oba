import { redirect } from "next/navigation";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";

/**
 * The profile is edited in the account app, which every Oba app shares, so
 * an old or bookmarked Kazan link lands there. It is another origin, so the
 * plain Next.js redirect is used rather than the locale-prefixing one.
 */
export default function ProfilePage(): never {
  redirect(ACCOUNT_LINKS.profile);
}
