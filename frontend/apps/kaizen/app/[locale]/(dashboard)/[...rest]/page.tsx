import { notFound } from "next/navigation";

/**
 * Takes every path that matches no route, such as `/foo` or a path one
 * segment deeper than any board page. It lives in the `(dashboard)` group so
 * that the not-found screen renders inside the app shell for a signed-in
 * user, and the layout's auth guard sends a signed-out user to sign in. Every
 * route with a fixed or dynamic segment wins over this one.
 */
export default function CatchAllPage() {
  return notFound();
}
