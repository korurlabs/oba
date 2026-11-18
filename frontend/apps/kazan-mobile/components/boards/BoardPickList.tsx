import * as React from "react";
import { View } from "react-native";

import {
  boardPickRows,
  type BoardPickItem,
  type BoardPickRow,
} from "@/components/boards/boardPickModel";
import { ListRow, ListSection } from "@/components/kazan/List";
import { SearchField } from "@/components/kazan/SearchField";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";

type BoardPickListCommon = {
  /** The boards in tree order; the caller decides which are disabled and why. */
  items: readonly BoardPickItem[];
  /** The section title above the rows. */
  header?: string;
  /** Helper text under the rows. */
  footer?: string;
  /** Blocks every row, such as while the choice is being sent. */
  disabled?: boolean;
};

type BoardPickListMultiProps = BoardPickListCommon & {
  mode?: "multi";
  /** The ticked boards, in the order they were picked. */
  picked: readonly string[];
  onToggle: (boardId: string) => void;
  /** Offers "Select all" next to the count when given. */
  onSelectAll?: () => void;
  /** Offers "Select none" next to the count when given. */
  onSelectNone?: () => void;
};

type BoardPickListSingleProps = BoardPickListCommon & {
  mode: "single";
  /** The picked board; null before one is picked. */
  value: string | null;
  onChange: (boardId: string) => void;
};

type BoardPickListProps = BoardPickListMultiProps | BoardPickListSingleProps;

/**
 * Boards to pick from, searched by name or prefix: a colour dot (muted for a
 * board without a colour), the name,
 * the prefix at the trailing edge, indented per level, with a line saying why
 * a board cannot be picked or what to mind. Multi mode ticks boards with
 * checkboxes under a "{{count}} selected" line; single mode picks one board
 * with radios.
 */
export function BoardPickList(props: BoardPickListProps) {
  const { t } = useTranslation();
  const [search, setSearch] = React.useState<string>("");
  const { items, header, footer, disabled = false } = props;
  const single: boolean = props.mode === "single";
  const picked: readonly string[] =
    props.mode === "single" ? (props.value ? [props.value] : []) : props.picked;
  const rows: BoardPickRow[] = boardPickRows(items, single ? "single" : "multi", picked, search);

  const press = (boardId: string) => {
    if (props.mode === "single") props.onChange(boardId);
    else props.onToggle(boardId);
  };

  return (
    <>
      <View className="pt-3">
        <SearchField label={t("boardPickSearch")} value={search} onChangeText={setSearch} />
      </View>
      {props.mode !== "single" ? (
        <View className="min-h-11 flex-row items-center justify-between gap-2 px-4 pt-2">
          <Text
            variant="footnote"
            className="text-muted-foreground"
            accessibilityLiveRegion="polite"
          >
            {t("boardPickSelected", { count: props.picked.length })}
          </Text>
          <View className="flex-row gap-1">
            {props.onSelectAll ? (
              <Button
                variant="ghost"
                size="sm"
                onPress={props.onSelectAll}
                disabled={disabled || items.every((item) => item.disabled)}
              >
                {t("boardPickSelectAll")}
              </Button>
            ) : null}
            {props.onSelectNone ? (
              <Button
                variant="ghost"
                size="sm"
                onPress={props.onSelectNone}
                disabled={disabled || props.picked.length === 0}
              >
                {t("boardPickSelectNone")}
              </Button>
            ) : null}
          </View>
        </View>
      ) : null}
      {rows.length === 0 ? (
        <Text variant="footnote" className="px-8 pt-6 text-center text-muted-foreground">
          {t("boardPickNoMatches")}
        </Text>
      ) : (
        <ListSection header={header} footer={footer} className={single ? undefined : "mt-2"}>
          {rows.map(({ item, checked }) => (
            <ListRow
              key={item.id}
              title={item.name}
              subtitle={item.note ?? undefined}
              subtitleTone={item.noteTone}
              value={item.prefix ?? undefined}
              dot="bg-muted-foreground"
              dotColor={item.color ?? undefined}
              indent={item.indent}
              accessory={single ? "radio" : "checkbox"}
              checked={checked}
              onPress={() => press(item.id)}
              disabled={disabled || item.disabled}
            />
          ))}
        </ListSection>
      )}
    </>
  );
}
