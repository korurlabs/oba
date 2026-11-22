"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import type { SelectOption } from "@oba/shared/features/form";
import { useRelation, useRelations } from "@/features/relations/hooks/use-relations";
import { relationPickerQuery } from "../lib/picker-queries";

/**
 * The options of a relation select. The organization may hold thousands of
 * relations, so the server searches them by name. The picked relation is
 * read on its own and kept among the options, as it may not be on the page
 * of the current search and the select shows only the label of an option.
 */
export function useRelationOptions(selectedId: string | null) {
  const tRelations = useTranslations("relations");
  const [search, setSearch] = useState("");
  const query = useMemo(() => relationPickerQuery(search), [search]);
  const relations = useRelations(query);
  const selected = useRelation(selectedId ?? "");
  const selectedRelation = selected.data?.data;

  const options = useMemo((): SelectOption[] => {
    const found = (relations.data?.data ?? []).map((relation) => ({
      value: relation.id,
      label: `${relation.name} · ${tRelations(`types.${relation.type}`)}`,
    }));
    if (selectedRelation && !found.some((option) => option.value === selectedRelation.id)) {
      found.unshift({
        value: selectedRelation.id,
        label: `${selectedRelation.name} · ${tRelations(`types.${selectedRelation.type}`)}`,
      });
    }
    return found;
  }, [relations.data?.data, selectedRelation, tRelations]);

  return {
    options,
    isLoading: relations.data === undefined && relations.isFetching,
    onSearchChange: setSearch,
    /** The failed search, to show beside the select. */
    error: relations.isError ? relations.error : null,
  };
}
