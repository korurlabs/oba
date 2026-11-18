"use client";

import { useQuery } from "@tanstack/react-query";

import type { RuleTab } from "@/features/scope/components/scope-files";
import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import {
  listAgentsFiles,
  listHooks,
  listRoles,
  listRules,
  listSubagents,
  listTemplates,
  listWorkflows,
} from "../api";
import type { RuleKindRows } from "../components/rule-rows";

/** The server reads one kind's rows of at most this many versions at once. */
export const RULE_KIND_VERSIONS_MAX = 20;

const KEYS = {
  list: (organizationId: string, kind: string, versionIds: readonly string[]) =>
    [...VERSIONED_KEYS.kinds(organizationId), kind, [...versionIds].sort()] as const,
};

function enabledFor(versionIds: readonly string[]): boolean {
  return versionIds.length > 0 && versionIds.length <= RULE_KIND_VERSIONS_MAX;
}

/** The rules the server reads from the named versions; disabled while none is named. */
export function useRules(versionIds: readonly string[]) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, "rules", versionIds),
    queryFn: () => listRules({ versionId: [...versionIds] }),
    enabled: enabledFor(versionIds),
  });
}

/** The `AGENTS.md` files of the named versions; disabled while none is named. */
export function useAgentsFiles(versionIds: readonly string[]) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, "agents-files", versionIds),
    queryFn: () => listAgentsFiles({ versionId: [...versionIds] }),
    enabled: enabledFor(versionIds),
  });
}

/** The roles of the named versions; disabled while none is named. */
export function useRoles(versionIds: readonly string[]) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, "roles", versionIds),
    queryFn: () => listRoles({ versionId: [...versionIds] }),
    enabled: enabledFor(versionIds),
  });
}

/** The subagents of the named versions; disabled while none is named. */
export function useSubagents(versionIds: readonly string[]) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, "subagents", versionIds),
    queryFn: () => listSubagents({ versionId: [...versionIds] }),
    enabled: enabledFor(versionIds),
  });
}

/** The workflows of the named versions; disabled while none is named. */
export function useWorkflows(versionIds: readonly string[]) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, "workflows", versionIds),
    queryFn: () => listWorkflows({ versionId: [...versionIds] }),
    enabled: enabledFor(versionIds),
  });
}

/** The templates of the named versions; disabled while none is named. */
export function useTemplates(versionIds: readonly string[]) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, "templates", versionIds),
    queryFn: () => listTemplates({ versionId: [...versionIds] }),
    enabled: enabledFor(versionIds),
  });
}

/** The hook scripts of the named versions; disabled while none is named. */
export function useHooks(versionIds: readonly string[]) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, "hooks", versionIds),
    queryFn: () => listHooks({ versionId: [...versionIds] }),
    enabled: enabledFor(versionIds),
  });
}

const NONE: readonly string[] = [];

/** The kind list of one Rules tab, or the error that kept it from loading. */
export type RuleTabKind = {
  kind: RuleKindRows | null;
  error: Error | null;
  refetch: () => void;
};

/**
 * The kind list a Rules tab shows, read for the versions that hold its files.
 * Only the open tab's list is queried; the others stay disabled.
 */
export function useRuleTabKind(tab: RuleTab, versionIds: readonly string[]): RuleTabKind {
  const ids = (forTab: RuleTab) => (forTab === tab ? versionIds : NONE);
  const rules = useRules(ids("rules"));
  const agentsFiles = useAgentsFiles(ids("agents-md"));
  const roles = useRoles(ids("roles"));
  const subagents = useSubagents(ids("subagents"));
  const workflows = useWorkflows(ids("workflows"));
  const templates = useTemplates(ids("templates"));
  const hooks = useHooks(ids("hooks"));
  const result = <T>(
    query: { data?: { data?: T | null }; error: Error | null; refetch: () => unknown },
    toKind: (rows: T) => RuleKindRows
  ): RuleTabKind => {
    const rows = query.data?.data;
    return {
      kind: rows === undefined || rows === null ? null : toKind(rows),
      error: query.error,
      refetch: () => void query.refetch(),
    };
  };
  switch (tab) {
    case "rules":
      return result(rules, (rows) => ({ tab, rows }));
    case "agents-md":
      return result(agentsFiles, (rows) => ({ tab, rows }));
    case "roles":
      return result(roles, (rows) => ({ tab, rows }));
    case "subagents":
      return result(subagents, (rows) => ({ tab, rows }));
    case "workflows":
      return result(workflows, (rows) => ({ tab, rows }));
    case "templates":
      return result(templates, (rows) => ({ tab, rows }));
    case "hooks":
      return result(hooks, (rows) => ({ tab, rows }));
  }
}
