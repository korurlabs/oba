"use client";

import { createContext, useContext } from "react";
import { useSession } from "next-auth/react";
import { resolveOrganizationId } from "@/lib/organization-id";

const ServerOrganizationIdContext = createContext<string | null>(null);

/**
 * Provides the default organization of the session the server rendered with,
 * so the first render can key its queries on it before the client has read
 * its own session.
 */
export function OrganizationIdProvider({
  organizationId,
  children,
}: {
  organizationId: string | null;
  children: React.ReactNode;
}) {
  return (
    <ServerOrganizationIdContext.Provider value={organizationId}>
      {children}
    </ServerOrganizationIdContext.Provider>
  );
}

/**
 * The organization every Ulak request is sent for: the session's default
 * organization, the same value the shared API client puts in
 * `X-Organization-ID`, or the server's value until the session is read.
 * Null when the user has no organization, so queries keyed on it wait.
 */
export function useOrganizationId(): string | null {
  const { data: session, status } = useSession();
  const serverOrganizationId = useContext(ServerOrganizationIdContext);
  return resolveOrganizationId({ status, session }, serverOrganizationId);
}
