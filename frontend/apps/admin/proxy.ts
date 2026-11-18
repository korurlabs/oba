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
  // the Admin page they asked for.
  invitationsPath: ACCOUNT_LINKS.invitations,
});

export const config = {
  matcher: "/((?!api|_next|_vercel|.*\\..*).*)",
};
