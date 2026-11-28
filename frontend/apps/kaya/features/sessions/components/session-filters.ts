import type { FilterCategory } from "@oba/ui/sky/filter-popover";

import { withChosen } from "@/components/common/list-parts";

import type { SessionFilterParams } from "../hooks/use-session-filter-params";
import type {
  RepositoryResponse,
  SessionFilterMachine,
  SessionFilterSubscription,
  SessionFilterUser,
  SessionFiltersResponse,
} from "../schemas";
import { subscriptionOptionLabel } from "./wall-data";

/** Every filter of the wall cleared, the search included. */
export const NO_SESSION_FILTERS: Partial<SessionFilterParams> = {
  search: null,
  repositoryId: null,
  userId: null,
  machineId: null,
  model: null,
  accountOrganizationUuid: null,
  ended: null,
};

/** Whether the search or a filter narrows the wall. */
export function sessionsFiltered(filters: SessionFilterParams): boolean {
  for (const key of Object.keys(NO_SESSION_FILTERS) as (keyof SessionFilterParams)[]) {
    if (filters[key] !== null) return true;
  }
  return false;
}

/**
 * The wall's filter categories, one per value the API offers. Each lists only
 * what the organization's sessions hold, and choosing a user narrows the
 * machines to that user's own.
 */
export function sessionFilterCategories(
  filters: SessionFilterParams,
  onChange: (change: Partial<SessionFilterParams>) => void,
  options: SessionFiltersResponse | undefined,
  repositories: readonly RepositoryResponse[] | undefined
): FilterCategory[] {
  const machines = (options?.machines ?? []).filter(
    (machine: SessionFilterMachine) => filters.userId === null || machine.userId === filters.userId
  );
  return [
    {
      key: "repositoryId",
      label: "Repository",
      mode: "single",
      searchable: true,
      options: withChosen(
        (repositories ?? []).map((repository: RepositoryResponse) => ({
          value: repository.id,
          label: repository.name,
        })),
        filters.repositoryId
      ),
      value: filters.repositoryId,
      onChange: (repositoryId: string | null) => onChange({ repositoryId }),
    },
    {
      key: "userId",
      label: "User",
      mode: "single",
      searchable: true,
      options: withChosen(
        (options?.users ?? []).map((user: SessionFilterUser) => ({
          value: user.id,
          label: user.displayName,
        })),
        filters.userId
      ),
      value: filters.userId,
      onChange: (userId: string | null) => onChange({ userId, machineId: null }),
    },
    {
      key: "machineId",
      label: "Machine",
      mode: "single",
      searchable: true,
      options: withChosen(
        machines.map((machine: SessionFilterMachine) => ({
          value: machine.id,
          label: machine.machineName,
        })),
        filters.machineId
      ),
      value: filters.machineId,
      onChange: (machineId: string | null) => onChange({ machineId }),
    },
    {
      key: "model",
      label: "Model",
      mode: "single",
      options: withChosen(
        (options?.models ?? []).map((model: string) => ({ value: model, label: model })),
        filters.model
      ),
      value: filters.model,
      onChange: (model: string | null) => onChange({ model }),
    },
    {
      key: "accountOrganizationUuid",
      label: "Subscription",
      mode: "single",
      options: withChosen(
        (options?.subscriptions ?? []).map((subscription: SessionFilterSubscription) => ({
          value: subscription.uuid,
          label: subscriptionOptionLabel(subscription),
        })),
        filters.accountOrganizationUuid
      ),
      value: filters.accountOrganizationUuid,
      onChange: (accountOrganizationUuid: string | null) => onChange({ accountOrganizationUuid }),
    },
    {
      key: "ended",
      label: "Session",
      mode: "single",
      options: [
        { value: "false", label: "Open" },
        { value: "true", label: "Ended" },
      ],
      value: filters.ended === null ? null : String(filters.ended),
      onChange: (value: string | null) =>
        onChange({ ended: value === null ? null : value === "true" }),
    },
  ];
}
