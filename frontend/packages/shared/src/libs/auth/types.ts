import "next-auth";
import "next-auth/jwt";

declare module "next-auth" {
  interface Session {
    accessToken?: string;
    expiresAt?: number;
    id_token?: string;
    user: {
      id?: string;
      name?: string;
      email?: string;
      image?: string | null;
      role?: string;
      verified?: boolean | null;
      defaultOrganizationId?: string | null;
    };
    error?: AuthError;
    sub?: string;
    userStatus?: string;
    pendingInvitationIds?: string[];
    remindLaterInvitations?: Record<string, number>; // invitationId -> remindAtMs
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    refreshToken?: string;
    accessToken?: string;
    expiresAt?: number;
    defaultOrganizationId?: string | null;
    email?: string;
    error?: AuthError;
    id_token?: string;
    verified?: boolean;
    userStatus?: string;
    pendingInvitationIds?: string[];
    remindLaterInvitations?: Record<string, number>; // invitationId -> remindAtMs
  }
}

export enum AuthError {
  REFRESH_TOKEN_ERROR = "REFRESH_TOKEN_ERROR",
  ONBOARDING_REQUIRED = "ONBOARDING_REQUIRED",
  UNEXPECTED_ERROR = "UNEXPECTED_ERROR",
}
