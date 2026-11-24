import { proxy } from "@oba/shared/proxy";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";

// `/` is public so its page can turn a session error from this proxy into a
// sign-in or onboarding redirect that keeps the callbackUrl.
const PUBLIC_ROUTES = ["/"];

export default proxy({
  publicRoutes: PUBLIC_ROUTES,
  loginPath: "/api/auth/login",
  authErrorPath: "/",
  // Invitations are answered in the account app, which returns the user to
  // the Kalkan page they asked for.
  invitationsPath: ACCOUNT_LINKS.invitations,
});

// Page slugs may contain dots (`isms/A.5.1`), so the proxy cannot skip every
// dotted path as the other Oba apps do: such a page would render without the
// sign-in check and without its locale. Only Next.js internals, the auth
// routes and this app's own static files are skipped, each by name and as a
// whole path segment, so a root page named `api-guide` or `assets-register`
// still passes through the proxy. The static files sit below `/assets/`, and
// `/assets` itself is the Asset section, so only the folder's contents are
// skipped.
export const config = {
  matcher: "/((?!(?:api|_next|_vercel)(?:/|$)|assets/|icon\\.png$|favicon\\.ico$).*)",
};
