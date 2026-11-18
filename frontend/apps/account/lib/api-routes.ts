/** Platform endpoints Account calls; the current user (`/users/me`) lives in `@oba/shared`. */
export const API_ROUTES = {
  onboarding: {
    complete: "/onboarding/complete",
  },
  organizations: {
    list: "/organizations",
  },
  userInvitations: {
    decision: "/users/me/invitations/decision",
  },
} as const;
