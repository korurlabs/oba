"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { Trophy } from "lucide-react";
import { toast } from "sonner";
import { SelectFieldStandalone } from "@oba/shared/features/form";
import { Button } from "@oba/ui/sky/button";
import { useChangeRelationType } from "../hooks/use-relations";
import { RelationType, RelationTypeSchema, type RelationDetail } from "../schemas";

function isRelationType(value: string): value is RelationType {
  return RelationTypeSchema.safeParse(value).success;
}

/**
 * What a type change did to the stage, which the result toast tells: a won
 * lead closes as a customer, a lead again restarts at NEW, a supplier that
 * becomes a customer is a direct one, and a supplier has no stage.
 */
export function typeChangeKey(
  from: RelationType,
  to: RelationType
): "leadToCustomer" | "toLead" | "toCustomer" | "toSupplier" {
  if (to === RelationType.LEAD) return "toLead";
  if (to === RelationType.SUPPLIER) return "toSupplier";
  return from === RelationType.LEAD ? "leadToCustomer" : "toCustomer";
}

/**
 * Changes the relation's type: "Mark as customer" for a lead that is won,
 * and a type select for any other change. The change applies at once; the
 * toast afterwards says what became of the stage, since the server resets or
 * clears it with the type.
 */
export function RelationTypeActions({ relation }: { relation: RelationDetail }) {
  const t = useTranslations("relations");
  const changeType = useChangeRelationType(relation.id);

  const typeOptions = useMemo(
    () => RelationTypeSchema.options.map((type) => ({ value: type, label: t(`types.${type}`) })),
    [t]
  );

  const changeTo = (type: RelationType) => {
    const from = relation.type;
    const name = relation.name;
    changeType.mutate(type, {
      onSuccess: () => {
        toast.success(t(`typeChange.done.${typeChangeKey(from, type)}`, { name }));
      },
    });
  };

  return (
    <div className="flex flex-wrap items-center gap-2">
      {relation.type === RelationType.LEAD && (
        <Button disabled={changeType.isPending} onClick={() => changeTo(RelationType.CUSTOMER)}>
          <Trophy aria-hidden="true" />
          {t("typeChange.markAsCustomer")}
        </Button>
      )}
      <SelectFieldStandalone
        value={relation.type}
        onValueChange={(value) => {
          if (isRelationType(value) && value !== relation.type) changeTo(value);
        }}
        options={typeOptions}
        disabled={changeType.isPending}
        className="w-40"
      />
    </div>
  );
}
