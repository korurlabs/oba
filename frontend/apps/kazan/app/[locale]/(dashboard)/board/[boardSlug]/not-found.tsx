import { NotFoundView } from "@/components/layout/not-found-view";

/**
 * Not-found screen of a page under a board that exists and the user may
 * see: an unknown sprint slug, or a ticket key that does not resolve. It
 * renders inside the board layout, so the board tabs stay on screen and the
 * user can switch to another tab of the same board. An unknown or
 * inaccessible board is caught a level up, by the dashboard's not-found
 * screen, because the board layout itself cannot render.
 */
export default function BoardNotFound() {
  return <NotFoundView className="h-full" />;
}
