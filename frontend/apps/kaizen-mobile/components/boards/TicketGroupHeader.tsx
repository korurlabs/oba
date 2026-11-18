import * as React from "react";
import { View } from "react-native";

import type { ParentGroup } from "@/components/boards/boardModel";
import { Badge } from "@/components/ui/badge";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

type TicketGroupHeaderProps = {
  group: ParentGroup;
  className?: string;
};

/**
 * The heading over sub-tickets whose parent sits in another sprint or the
 * backlog: the parent's key and title, struck through once the parent is
 * done, a "Parent" tag that sets it apart from the ticket cards under it, and
 * how many of its sub-tickets are done. It is not a ticket row, so
 * it takes no taps and no drags.
 */
function TicketGroupHeader({ group, className }: TicketGroupHeaderProps) {
  const { t } = useTranslation();
  const progress = group.progress;
  return (
    <View
      accessible
      accessibilityLabel={[
        `${group.parent.key} ${group.parent.title}`,
        t("boardGroupParentTag"),
        progress ? t("boardSubTicketsDone", progress) : null,
      ]
        .filter(Boolean)
        .join(", ")}
      className={cn("flex-row items-center gap-2 px-4 pb-1.5 pt-1", className)}
    >
      <Text variant="ticket-key">{group.parent.key}</Text>
      <Text
        variant="footnote"
        numberOfLines={1}
        className={cn(
          "flex-1 font-sans-medium text-muted-foreground",
          group.parentDone && "line-through"
        )}
      >
        {group.parent.title}
      </Text>
      <Badge
        variant="tag-sm"
        className="border border-border px-[5px]"
        textClassName="text-muted-foreground"
      >
        {t("boardGroupParentTag")}
      </Badge>
      {progress ? (
        <Text variant="caption" className="text-muted-foreground">
          {t("ticketSubTicketProgress", progress)}
        </Text>
      ) : null}
    </View>
  );
}

export { TicketGroupHeader };
export type { TicketGroupHeaderProps };
