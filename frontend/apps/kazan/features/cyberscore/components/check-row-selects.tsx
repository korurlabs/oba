"use client";

import { useTranslations } from "next-intl";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  CHECK_OUTCOMES,
  CHECK_STATUSES,
  CheckOutcomeSchema,
  CheckStatusSchema,
  type CheckOutcome,
  type CheckStatus,
} from "../schemas";

interface RowSelectProps<T> {
  value: T;
  /** Names the row for screen readers, since every row has the same select. */
  checkName: string;
  disabled?: boolean;
  onChange: (value: T) => void;
}

/** The outcome of one check in the list; a change saves it and moves the score. */
export function CheckOutcomeSelect({
  value,
  checkName,
  disabled,
  onChange,
}: RowSelectProps<CheckOutcome>) {
  const t = useTranslations("cyberscore");

  const handleChange = (next: string) => {
    const parsed = CheckOutcomeSchema.safeParse(next);
    if (parsed.success && parsed.data !== value) onChange(parsed.data);
  };

  return (
    <Select value={value} onValueChange={handleChange} disabled={disabled}>
      <SelectTrigger
        size="sm"
        className="w-36"
        aria-label={`${t("checks.columns.outcome")}: ${checkName}`}
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {CHECK_OUTCOMES.map((outcome) => (
          <SelectItem key={outcome} value={outcome}>
            {t(`outcomes.${outcome}`)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

/** The workflow status of one check in the list; it never moves the score. */
export function CheckStatusSelect({
  value,
  checkName,
  disabled,
  onChange,
}: RowSelectProps<CheckStatus>) {
  const t = useTranslations("cyberscore");

  const handleChange = (next: string) => {
    const parsed = CheckStatusSchema.safeParse(next);
    if (parsed.success && parsed.data !== value) onChange(parsed.data);
  };

  return (
    <Select value={value} onValueChange={handleChange} disabled={disabled}>
      <SelectTrigger
        size="sm"
        className="w-36"
        aria-label={`${t("checks.columns.status")}: ${checkName}`}
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {CHECK_STATUSES.map((status) => (
          <SelectItem key={status} value={status}>
            {t(`statuses.${status}`)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
