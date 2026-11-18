/**
 * Not-found screen of every Admin page. An unknown path or an organization
 * the user cannot see lands here.
 */

import { NotFoundView } from "@/components/layout/not-found-view";

export default function LocaleNotFound() {
  return <NotFoundView className="min-h-screen" />;
}
