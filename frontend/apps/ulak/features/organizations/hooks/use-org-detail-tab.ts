"use client";

import { useCallback } from "react";
import { parseAsStringLiteral, useQueryState } from "nuqs";

/** Search-param key of the organization detail tabs. */
const ORG_DETAIL_TAB_PARAM = "tab";

const ORG_DETAIL_TABS = [
  "detail",
  "members",
  "invitations",
  "legalEntities",
  "groups",
  "access",
] as const;

export type OrgDetailTab = (typeof ORG_DETAIL_TABS)[number];

/**
 * Tabs that only an OWNER or ADMIN of the organization sees: groups and app
 * grants can be read only with that role.
 */
const MANAGER_TABS: readonly OrgDetailTab[] = ["groups", "access"];

/**
 * The tab a GUEST does not see: legal entities can be read only by a MEMBER
 * or a higher role.
 */
const LEGAL_ENTITIES_TAB: OrgDetailTab = "legalEntities";

const DEFAULT_TAB: OrgDetailTab = "detail";

/**
 * Which tab of the organization detail is open, as URL state (`?tab=`).
 *
 * The contract: an unknown value falls back to `detail`, and so does a
 * manager tab when `canManage` is false and the legal entities tab when
 * `canReadLegalEntities` is false (a shared link must not open a tab the
 * viewer cannot see). The default tab removes the parameter (nuqs clears
 * a param that is at its default), and a tab change replaces the history
 * entry instead of adding one.
 */
export function useOrgDetailTab({
  canManage,
  canReadLegalEntities,
}: {
  canManage: boolean;
  canReadLegalEntities: boolean;
}) {
  const [requestedTab, setActiveTab] = useQueryState(
    ORG_DETAIL_TAB_PARAM,
    parseAsStringLiteral(ORG_DETAIL_TABS).withDefault(DEFAULT_TAB)
  );

  const isHidden =
    (!canManage && MANAGER_TABS.includes(requestedTab)) ||
    (!canReadLegalEntities && requestedTab === LEGAL_ENTITIES_TAB);
  const activeTab = isHidden ? DEFAULT_TAB : requestedTab;

  const setTab = useCallback(
    (tab: OrgDetailTab) => {
      void setActiveTab(tab);
    },
    [setActiveTab]
  );

  return { activeTab, setTab };
}
