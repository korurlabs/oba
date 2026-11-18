import { proxy } from "@oba/shared/proxy";

// `/` is public so its page can turn a session error from this proxy into a
// sign-in or onboarding redirect that keeps the callbackUrl.
const PUBLIC_ROUTES = ["/", "/onboarding"];

export default proxy({
  publicRoutes: PUBLIC_ROUTES,
  loginPath: "/api/auth/login",
  authErrorPath: "/",
  invitationsPath: "/invitations",
});

export const config = {
  matcher: "/((?!api|_next|_vercel|.*\\..*).*)",
};
