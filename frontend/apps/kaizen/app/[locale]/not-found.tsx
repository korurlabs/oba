/**
 * Not-found screen of the pages outside the app shell (the public home, the
 * downloads page). Unknown paths do not land here: the catch-all
 * page lives in the `(dashboard)` group, so they get the not-found screen
 * inside the shell.
 */

import { NotFoundView } from "@/components/layout/not-found-view";

export default function LocaleNotFound() {
  return <NotFoundView className="min-h-screen" />;
}
