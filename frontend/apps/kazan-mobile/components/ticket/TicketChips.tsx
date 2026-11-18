import { ChevronDown, Ruler } from "lucide-react-native";
import * as React from "react";
import { Pressable, View } from "react-native";

import { PriorityIcon, StatusPill, TicketTypeBadge } from "@/components/kazan/TicketMeta";
import { resolvePriority } from "@/components/kazan/ticketMetaModel";
import type { ChoiceKind } from "@/components/ticket/ticketPickers";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

/** Reaches the 44 pt iOS and 48 dp Android touch height from the 24 pt chip. */
const CHIP_HIT_SLOP = { top: 12, bottom: 12, left: 4, right: 4 } as const;

type MetaChipProps = {
  accessibilityLabel: string;
  /** Adds a chevron and makes the chip a button that opens its picker. */
  onPress?: () => void;
  /** A destructive outline, for a required value that is missing or was refused. */
  invalid?: boolean;
  children: React.ReactNode;
};

/** The outlined 24 pt pill of the header's priority and mission rank. */
function MetaChip({ accessibilityLabel, onPress, invalid = false, children }: MetaChipProps) {
  const className = cn(
    "min-h-6 flex-row items-center gap-1 self-start rounded-full border px-2.5",
    invalid ? "border-destructive" : "border-border"
  );
  if (!onPress) {
    return (
      <View accessible accessibilityLabel={accessibilityLabel} className={className}>
        {children}
      </View>
    );
  }
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      hitSlop={CHIP_HIT_SLOP}
      onPress={onPress}
      className={cn(className, "active:opacity-70")}
    >
      {children}
      <Icon as={ChevronDown} size={14} className="text-muted-foreground" />
    </Pressable>
  );
}

type PriorityChipProps = {
  priority: string | null | undefined;
  onPress?: () => void;
};

/** A ticket priority: its coloured mark and name in an outlined pill; nothing when unknown. */
function PriorityChip({ priority, onPress }: PriorityChipProps) {
  const { t } = useTranslation();
  const meta = resolvePriority(priority);
  if (!meta) return null;
  return (
    <MetaChip
      accessibilityLabel={t("ticketPriorityValue", { priority: t(meta.labelKey) })}
      onPress={onPress}
    >
      <PriorityIcon priority={priority} size={14} showLabel />
    </MetaChip>
  );
}

type RankChipProps = {
  rank: string | null | undefined;
  onPress?: () => void;
  /** Without a rank: "Mission rank" with a required mark, in place of the rank. */
  required?: boolean;
  invalid?: boolean;
};

/**
 * A mission rank (XS to XXL) after a ruler in an outlined pill. Without a
 * rank it reads "Mission rank", so the missing value is named; nothing at all
 * when it is also read-only.
 */
function RankChip({ rank, onPress, required = false, invalid = false }: RankChipProps) {
  const { t } = useTranslation();
  if (!rank && !onPress) return null;
  return (
    <MetaChip
      accessibilityLabel={rank ? t("ticketMissionRank", { rank }) : t("ticketSizingTitle")}
      onPress={onPress}
      invalid={invalid}
    >
      <Icon
        as={Ruler}
        size={14}
        className={invalid ? "text-destructive" : "text-muted-foreground"}
      />
      {rank ? (
        <Text className="font-mono-medium text-meta text-foreground">{rank}</Text>
      ) : (
        <Text
          className={cn(
            "font-sans-medium text-meta",
            invalid ? "text-destructive" : "text-muted-foreground"
          )}
        >
          {t("ticketSizingTitle")}
          {required ? <Text className="text-destructive"> *</Text> : null}
        </Text>
      )}
    </MetaChip>
  );
}

/** A ticket value drawn as the chip the app shows it with everywhere: type, status, priority or rank. */
function ChoiceChip({ kind, value }: { kind: ChoiceKind; value: string }) {
  switch (kind) {
    case "type":
      return <TicketTypeBadge type={value} />;
    case "status":
      return <StatusPill status={value} />;
    case "priority":
      return <PriorityChip priority={value} />;
    case "rank":
      return <RankChip rank={value} />;
  }
}

export { ChoiceChip, PriorityChip, RankChip };
