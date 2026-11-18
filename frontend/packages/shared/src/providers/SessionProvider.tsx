"use client";

import { SessionProvider as AuthJsSessionProvider } from "next-auth/react";
import React from "react";
import { SessionGuard } from "./SessionGuard";

export function SessionProvider({
  children,
  enableSessionGuard = true,
}: {
  children: React.ReactNode;
  enableSessionGuard?: boolean;
}) {
  return (
    <AuthJsSessionProvider refetchOnWindowFocus={true} refetchWhenOffline={false}>
      {enableSessionGuard ? (
        <SessionGuard>{children}</SessionGuard>
      ) : (
        children
      )}
    </AuthJsSessionProvider>
  );
}
