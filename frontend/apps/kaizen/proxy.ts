import { proxy } from "@oba/shared/proxy";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";

const PUBLIC_ROUTES = ["/", "/downloads"];

export default proxy({
  publicRoutes: PUBLIC_ROUTES,
  loginPath: "/api/auth/login",
  authErrorPath: "/",
  // Invitations are answered in the account app, which returns the user to
  // the Kaizen page they asked for.
  invitationsPath: ACCOUNT_LINKS.invitations,
});

export const config = {
  matcher: "/((?!api|_next|_vercel|.*\\..*).*)",
};
