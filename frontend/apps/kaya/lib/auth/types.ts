import "next-auth";
import "next-auth/jwt";

import { AuthError } from "@oba/shared/libs/auth/types";

/**
 * Kaya shares the session error enum with the shared auth module: both augment
 * next-auth's Session and JWT, and TypeScript requires the two declarations of
 * `error` to have the same type once a shared component brings both into one program.
 */
export { AuthError };

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
