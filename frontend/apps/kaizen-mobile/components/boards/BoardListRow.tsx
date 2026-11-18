import * as React from "react";
import { Platform } from "react-native";

import { boardIcon, defaultBoardIcon } from "@/components/boards/boardIcons";
import type { BoardRow } from "@/components/boards/boardModel";
import { boardSubtitleParts, type BoardSubtitlePart } from "@/components/boards/boardRowModel";
import { useSprintDates } from "@/components/boards/SprintHeader";
import { ListRow } from "@/components/kaizen/List";
import { useTranslation } from "@/i18n";
import { DEFAULT_BOARD_COLOR, type Sprint } from "@/lib/schemas/boards";

type BoardListRowProps = {
  row: BoardRow;
  /** The board's running sprint; null when it has none, undefined while unknown. */
  activeSprint: Sprint | null | undefined;
  /** Open root tickets; undefined while unknown. */
  openCount: number | undefined;
  /** Recent and Pinned rows say less than All boards rows; see `boardSubtitleParts`. */
  variant: "recent" | "all";
  onPress: (row: BoardRow) => void;
  /** Opens the board's menu: pin, rename, settings. */
  onLongPress?: (row: BoardRow) => void;
};

/**
 * A board in the Boards list: its name and a one-line subtitle with the facts
 * that help pick it. iOS draws the board's glyph on a tile of the board's own
 * colour and shows the open tickets as the All boards row value; Android
 * draws the board's own icon and puts the open tickets in the subtitle.
 */
function BoardListRowBase({
  row,
  activeSprint,
  openCount,
  variant,
  onPress,
  onLongPress,
}: BoardListRowProps) {
  const { t } = useTranslation();
  const { left } = useSprintDates(activeSprint ?? null);
  const ios: boolean = Platform.OS === "ios";

  const parts: BoardSubtitlePart[] = boardSubtitleParts(ios ? "ios" : "android", variant, {
    prefix: row.prefix,
    parentName: row.parentName,
    childCount: row.childCount,
    sprint:
      activeSprint === undefined
        ? undefined
        : activeSprint
          ? { name: activeSprint.name, daysLeft: left || null }
          : null,
    openCount,
  });
  const subtitle: string = parts
    .map((part: BoardSubtitlePart): string => {
      switch (part.kind) {
        case "key":
          return part.prefix;
        case "parent":
          return ios
            ? t("boardsInParent", { name: part.name })
            : t("boardsChildOf", { name: part.name });
        case "childCount":
          return t("boardsChildCount", { count: part.count });
        case "sprint":
          return part.name;
        case "daysLeft":
          return part.text;
        case "noSprint":
          return t("boardsNoActiveSprint");
        case "open":
          return t("boardsOpenCount", { count: part.count });
      }
    })
    .join(" · ");

  return (
    <ListRow
      title={row.name}
      subtitle={subtitle || undefined}
      subtitleLines={1}
      icon={ios ? defaultBoardIcon(row) : boardIcon(row)}
      iconColor={row.color ?? DEFAULT_BOARD_COLOR}
      value={ios && variant === "all" && openCount !== undefined ? String(openCount) : undefined}
      onPress={() => onPress(row)}
      onLongPress={onLongPress ? () => onLongPress(row) : undefined}
    />
  );
}

const BoardListRow = React.memo(BoardListRowBase);

export { BoardListRow };
export type { BoardListRowProps };
