import * as React from "react";
import { View } from "react-native";
import { useReducedMotion } from "react-native-reanimated";

import {
  resolveStatus,
  TICKET_STATUSES,
  type TicketStatus,
} from "@/components/kazan/ticketMetaModel";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

/** A box in window coordinates. */
type WindowRect = { x: number; y: number; width: number; height: number };

type DropStripProps = {
  /** The ticket count per status. */
  counts: Record<TicketStatus, number>;
  /** The status the dragged ticket comes from. */
  from: TicketStatus;
  /** The status under the finger, if any. */
  over: TicketStatus | null;
  /** Reports where each chip sits on screen, for the drop hit test. */
  onMeasure: (status: TicketStatus, rect: WindowRect) => void;
  className?: string;
};

/**
 * Every status as a drop target while a ticket is dragged, so a far column is
 * one gesture away. A drop on a chip appends the ticket to that status. The
 * source status is dimmed; the chip under the finger is outlined in primary
 * and grows a little, unless Reduce Motion is on. Labels wrap to three lines
 * so the longer Dutch and Turkish names stay whole.
 */
function DropStrip({ counts, from, over, onMeasure, className }: DropStripProps) {
  const { t } = useTranslation();
  return (
    <View
      accessibilityLabel={t("boardDropStrip")}
      className={cn("flex-row gap-1.5 bg-screen px-2 py-2", className)}
    >
      {TICKET_STATUSES.map((status) => (
        <StripChip
          key={status}
          status={status}
          label={t(resolveStatus(status).labelKey)}
          count={counts[status]}
          source={status === from}
          over={status === over}
          onMeasure={onMeasure}
        />
      ))}
    </View>
  );
}

type StripChipProps = {
  status: TicketStatus;
  label: string;
  count: number;
  source: boolean;
  over: boolean;
  onMeasure: (status: TicketStatus, rect: WindowRect) => void;
};

const OVER_STYLE = { transform: [{ scale: 1.05 }] } as const;

function StripChip({ status, label, count, source, over, onMeasure }: StripChipProps) {
  const ref = React.useRef<View>(null);
  const reduceMotion: boolean = useReducedMotion();
  const measure = () =>
    ref.current?.measureInWindow((x, y, width, height) =>
      onMeasure(status, { x, y, width, height })
    );
  return (
    <View
      ref={ref}
      onLayout={measure}
      style={over && !reduceMotion ? OVER_STYLE : undefined}
      className={cn(
        "min-h-14 flex-1 items-center justify-center gap-0.5 rounded-md border px-1 py-1.5",
        over ? "border-primary bg-primary-light" : "border-border bg-card",
        source && !over ? "opacity-50" : null
      )}
    >
      <View className={cn("size-2 rounded-full", resolveStatus(status).dotClassName)} />
      <Text variant="caption-2" numberOfLines={3} className="text-center">
        {label}
      </Text>
      <Text variant="caption-2" className="text-muted-foreground">
        {count}
      </Text>
    </View>
  );
}

export { DropStrip };
export type { DropStripProps, WindowRect };
