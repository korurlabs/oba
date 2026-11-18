"use client";

import { useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

import { setCurrentOrganizationId } from "@/lib/organization";

import {
  hasRole,
  type MembershipResponse,
  type OrganizationRole,
  type UserMeResponse,
} from "./schemas";

export type Identity = {
  me: UserMeResponse;
  /** The organization every call is made in. */
  organization: MembershipResponse;
  /** Switches to another of the user's organizations and drops every cached answer of the old one. */
  switchOrganization: (organizationId: string) => void;
  /** Whether the user's role in the current organization is at least `role`. */
  can: (role: OrganizationRole) => boolean;
};

const IdentityContext = createContext<Identity | null>(null);

/**
 * The membership to start with: the user's default organization on the
 * platform when Kaya may open it, else the first one Kaya may open.
 */
function initialMembership(me: UserMeResponse): MembershipResponse {
  const membership =
    me.memberships.find(
      (entry: MembershipResponse) => entry.organizationId === me.defaultOrganizationId
    ) ?? me.memberships[0];
  if (membership === undefined) {
    throw new Error("IdentityProvider needs at least one membership");
  }
  return membership;
}

/**
 * Holds the signed-in user and the chosen organization. The choice is handed
 * to the API client before any child renders, so the first organization call
 * already carries the right `X-Organization-ID`.
 */
export function IdentityProvider({ me, children }: { me: UserMeResponse; children: ReactNode }) {
  const queryClient = useQueryClient();
  const [organization, setOrganization] = useState<MembershipResponse>(() => {
    const membership = initialMembership(me);
    setCurrentOrganizationId(membership.organizationId);
    return membership;
  });

  const switchOrganization = useCallback(
    (organizationId: string) => {
      const next = me.memberships.find(
        (entry: MembershipResponse) => entry.organizationId === organizationId
      );
      if (next === undefined || next.organizationId === organization.organizationId) {
        return;
      }
      setCurrentOrganizationId(next.organizationId);
      // Answers of the old organization must never show under the new one;
      // the identity query is kept, since it does not depend on the organization.
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== "identity" });
      setOrganization(next);
    },
    [me.memberships, organization.organizationId, queryClient]
  );

  const value = useMemo<Identity>(
    () => ({
      me,
      organization,
      switchOrganization,
      can: (role: OrganizationRole) => hasRole(organization.role, role),
    }),
    [me, organization, switchOrganization]
  );

  return <IdentityContext.Provider value={value}>{children}</IdentityContext.Provider>;
}

/** The signed-in user and organization; only inside the dashboard, below the identity gate. */
export function useIdentity(): Identity {
  const identity = useContext(IdentityContext);
  if (identity === null) {
    throw new Error("useIdentity is used outside IdentityProvider");
  }
  return identity;
}
