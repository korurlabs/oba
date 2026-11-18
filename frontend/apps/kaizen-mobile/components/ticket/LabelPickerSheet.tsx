import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { toggleValue } from "@/components/boards/boardModel";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { LabelDot } from "@/components/ticket/LabelDot";
import { sameIds } from "@/components/ticket/ticketActionsModel";
import { labelIdsOf, labelsFor } from "@/components/ticket/ticketFieldsModel";
import { useDraftTicketField, useSavedTicketField } from "@/components/ticket/useFieldSheet";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoardLabels } from "@/hooks/queries/boards";
import { useTranslation } from "@/i18n";
import type { BoardOption } from "@/lib/schemas/boards";

type LabelPickerSheetProps = {
  /** The board whose labels are listed. */
  boardId: string | null | undefined;
  /** The attached label ids; undefined while the ticket loads. */
  value: readonly string[] | undefined;
  /** The labels attached now, so one the board no longer lists can still be removed. */
  attached?: readonly BoardOption[];
  /** Receives the full new list on Done, only when it differs from `value`. */
  onChange: (labelIds: string[], options: readonly BoardOption[]) => void;
  /** A spinner on Done while the change is saved. */
  saving?: boolean;
  /** Closes the sheet once true, such as when the change has landed. */
  closed: boolean;
  /** Why the ticket could not be read, with its retry. */
  loadError?: unknown;
  onRetryLoad?: () => void;
};

/**
 * The label picker of a ticket: the board's labels, any number picked,
 * handed over on Done as the full list, so unticking every label removes
 * them all. Done without a change only closes it.
 */
export function LabelPickerSheet({
  boardId,
  value,
  attached = [],
  onChange,
  saving = false,
  closed,
  loadError,
  onRetryLoad,
}: LabelPickerSheetProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const labels = useBoardLabels(boardId ?? undefined);
  const [picked, setPicked] = React.useState<string[] | null>(null);
  const [unchangedDone, setUnchangedDone] = React.useState<boolean>(false);

  const currentIds: readonly string[] = value ?? [];
  const selected: string[] = picked ?? [...currentIds];
  const changed: boolean = picked !== null && !sameIds(picked, currentIds);
  const options: BoardOption[] = [
    ...(labels.data ?? []),
    ...attached.filter((label) => !(labels.data ?? []).some((option) => option.id === label.id)),
  ];

  const done = () => {
    if (changed) onChange(selected, options);
    else setUnchangedDone(true);
  };

  const failure: unknown = loadError ?? labels.error;

  return (
    <BottomSheet
      title={t("ticketFieldLabels")}
      confirmLabel={t("ticketDone")}
      onConfirm={done}
      confirmDisabled={!value}
      confirmLoading={saving}
      detent="medium"
      dirty={changed && !closed}
      scroll
    >
      <DismissWhen when={closed || unchangedDone} />
      {failure ? (
        <InlineError
          message={t("ticketLabelsError")}
          onRetry={() => {
            if (loadError) onRetryLoad?.();
            else void labels.refetch();
          }}
          className="m-4"
        />
      ) : !value || labels.isPending ? (
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      ) : options.length === 0 ? (
        <Text className="px-6 py-8 text-center font-sans text-subhead text-muted-foreground">
          {t("ticketLabelsEmpty")}
        </Text>
      ) : (
        <ListSection>
          {options.map((label) => (
            <ListRow
              key={label.id}
              title={label.name}
              leading={<LabelDot colour={label.color} />}
              accessory="check"
              checked={selected.includes(label.id)}
              onPress={() => setPicked(toggleValue(selected, label.id))}
            />
          ))}
        </ListSection>
      )}
    </BottomSheet>
  );
}

/** The label picker over a saved ticket: Done saves the list at once. */
export function TicketLabelsSheet() {
  const { ticketRef, ticket, error, retry, saving, saved, save } = useSavedTicketField();
  return (
    <LabelPickerSheet
      boardId={ticketRef?.boardId}
      value={ticket ? labelIdsOf(ticket) : undefined}
      attached={ticket?.labels ?? []}
      onChange={(labelIds, options) =>
        save({
          body: { labelIds },
          optimistic: (detail) => ({
            ...detail,
            labels: labelsFor(labelIds, options, detail.labels ?? []),
          }),
        })
      }
      saving={saving}
      closed={saved}
      loadError={error}
      onRetryLoad={() => void retry()}
    />
  );
}

/** The label picker over the New ticket draft: Done keeps the list for Create. */
export function DraftLabelsSheet() {
  const { fields, save, closed } = useDraftTicketField();
  return (
    <LabelPickerSheet
      boardId={fields?.boardId}
      value={fields?.labelIds}
      onChange={(labelIds) => save({ labelIds })}
      closed={closed}
    />
  );
}
