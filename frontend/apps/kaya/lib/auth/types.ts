import "next-auth";
import "next-auth/jwt";

/** Why a session can no longer call Kaya's API. */
export const AuthError = {
  /** The refresh token was refused or is gone: the user signs in again. */
  REFRESH_TOKEN_ERROR: "REFRESH_TOKEN_ERROR",
} as const;
export type AuthError = (typeof AuthError)[keyof typeof AuthError];

declare module "next-auth" {
  interface Session {
    accessToken?: string;
    expiresAt?: number;
    error?: AuthError;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken?: string;
    refreshToken?: string;
    expiresAt?: number;
    idToken?: string;
    error?: AuthError;
  }
}
