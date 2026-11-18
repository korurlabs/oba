import {
  Bug,
  ChevronDown,
  ChevronsDown,
  ChevronsUp,
  ChevronUp,
  CircleAlert,
  Minus,
  RefreshCw,
  Siren,
  Sparkles,
  TrendingUp,
  TriangleAlert,
  Wrench,
} from "lucide-react-native";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Pressable, View } from "react-native";

import {
  resolveHealth,
  resolvePriority,
  resolveStatus,
  resolveTicketType,
  labelColour,
  type TicketPriority,
  type TicketType,
} from "@/components/kazan/ticketMetaModel";
import { Badge } from "@/components/ui/badge";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

type StatusPillProps = {
  status: string | null | undefined;
  /** `pill` is a tinted pill with a dot; `dot` is the dot and label without ground. */
  variant?: "pill" | "dot";
  /** Adds a chevron and makes the pill a button that opens the status picker. */
  onPress?: () => void;
  className?: string;
};

/** A ticket status: Todo, In Progress, Waiting for 3rd party, In Review or Done. */
function StatusPill({ status, variant = "pill", onPress, className }: StatusPillProps) {
  const { t } = useTranslation();
  const meta = resolveStatus(status);
  const label = t(meta.labelKey);

  // The dot form sits in the card's meta row, so it takes that row's caption
  // size and lets the row centre it beside the time and the avatars.
  const content = (
    <>
      <View className={cn("size-2 rounded-full", meta.dotClassName)} />
      {variant === "dot" ? (
        <Text variant="caption" className="text-foreground">
          {label}
        </Text>
      ) : (
        <Text className="font-sans-medium text-meta text-foreground">{label}</Text>
      )}
      {onPress ? <Icon as={ChevronDown} size={14} className="text-muted-foreground" /> : null}
    </>
  );
  const boxClassName = cn(
    "flex-row items-center",
    variant === "pill"
      ? cn("min-h-6 gap-1.5 self-start rounded-full px-2.5", meta.pillClassName)
      : "gap-1",
    className
  );

  if (!onPress) {
    return (
      <View accessible accessibilityLabel={label} className={boxClassName}>
        {content}
      </View>
    );
  }
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      hitSlop={10}
      onPress={onPress}
      className={cn(boxClassName, "active:opacity-60")}
    >
      {content}
    </Pressable>
  );
}

const TYPE_ICON: Record<TicketType, LucideIcon> = {
  BUG: Bug,
  FEATURE: Sparkles,
  TECH_DEBT: Wrench,
  IMPROVEMENT: TrendingUp,
  INCIDENT: Siren,
  PROBLEM: CircleAlert,
  REFACTOR: RefreshCw,
};

type TicketTypeBadgeProps = {
  type: string | null | undefined;
  /** `md` is the 20 pt tag of cards and detail; `sm` the 18 pt tag of dense rows. */
  size?: "md" | "sm";
  /** Shows only the icon on the type's ground, for the leading mark of a row. */
  iconOnly?: boolean;
  className?: string;
};

/** A ticket type tag in its type colours. TASK and unknown types use Feature's. */
function TicketTypeBadge({ type, size = "md", iconOnly = false, className }: TicketTypeBadgeProps) {
  const { t } = useTranslation();
  const meta = resolveTicketType(type);
  const label = meta.rawLabel ?? t(meta.labelKey);

  if (iconOnly) {
    return (
      <View
        accessible
        accessibilityLabel={label}
        className={cn("size-6 items-center justify-center rounded-xs", meta.bgClassName, className)}
      >
        <Icon as={TYPE_ICON[meta.type]} size={14} className={meta.fgClassName} />
      </View>
    );
  }
  return (
    <Badge
      variant={size === "sm" ? "tag-sm" : "tag"}
      className={cn(meta.bgClassName, className)}
      textClassName={meta.fgClassName}
      accessibilityLabel={label}
    >
      {label}
    </Badge>
  );
}

const PRIORITY_ICON: Record<TicketPriority, LucideIcon> = {
  LOWEST: ChevronsDown,
  LOW: ChevronDown,
  MEDIUM: Minus,
  HIGH: ChevronUp,
  HIGHEST: ChevronsUp,
  P1: TriangleAlert,
  P2: TriangleAlert,
  P3: TriangleAlert,
};

type PriorityIconProps = {
  priority: string | null | undefined;
  size?: number;
  /** Shows the label after the icon, as in the detail screen and pickers. */
  showLabel?: boolean;
  className?: string;
};

/** The priority mark in its priority colour; nothing for a missing priority. */
function PriorityIcon({ priority, size = 16, showLabel = false, className }: PriorityIconProps) {
  const { t } = useTranslation();
  const meta = resolvePriority(priority);
  if (!meta) return null;
  const label = t(meta.labelKey);
  return (
    <View
      accessible
      accessibilityLabel={label}
      className={cn("flex-row items-center gap-1", className)}
    >
      <Icon as={PRIORITY_ICON[meta.priority]} size={size} className={meta.className} />
      {showLabel ? (
        <Text className="font-sans-medium text-meta text-foreground">{label}</Text>
      ) : null}
    </View>
  );
}

/** The mission rank (effort size) as a mono outline tag: XS, S, M, L, XL or XXL. */
function MissionRank({ rank, className }: { rank: string | null | undefined; className?: string }) {
  if (!rank) return null;
  return (
    <Badge variant="outline" className={className}>
      {rank}
    </Badge>
  );
}

/** A milestone's health: On track, At risk, Overdue or Done; nothing when unknown. */
function HealthBadge({
  health,
  className,
}: {
  health: string | null | undefined;
  className?: string;
}) {
  const { t } = useTranslation();
  const meta = resolveHealth(health);
  if (!meta) return null;
  const label = t(meta.labelKey);
  return (
    <View
      accessible
      accessibilityLabel={label}
      className={cn(
        "min-h-6 flex-row items-center gap-1.5 self-start rounded-full px-2.5",
        meta.pillClassName,
        className
      )}
    >
      <View className={cn("size-2 rounded-full", meta.dotClassName)} />
      <Text className="font-sans-medium text-meta text-foreground">{label}</Text>
    </View>
  );
}

type LabelChipProps = {
  name: string;
  /** The label's colour from the API, such as "#e5484d"; a grey dot when invalid. */
  colour?: string | null;
  className?: string;
};

/** A ticket label: its colour as a dot (user data, not a token) and its name. */
function LabelChip({ name, colour, className }: LabelChipProps) {
  const dot = labelColour(colour);
  return (
    <View
      accessible
      accessibilityLabel={name}
      className={cn(
        "min-h-6 flex-row items-center gap-1.5 self-start rounded-full border border-border px-2",
        className
      )}
    >
      <View
        className={cn("size-2 rounded-full", !dot && "bg-muted-foreground")}
        style={dot ? { backgroundColor: dot } : undefined}
      />
      <Text numberOfLines={1} className="font-sans-medium text-meta text-foreground">
        {name}
      </Text>
    </View>
  );
}

/** The icon of a ticket type, for pickers that draw their own row. */
function ticketTypeIcon(type: string | null | undefined): LucideIcon {
  return TYPE_ICON[resolveTicketType(type).type];
}

export {
  HealthBadge,
  LabelChip,
  MissionRank,
  PriorityIcon,
  StatusPill,
  TicketTypeBadge,
  ticketTypeIcon,
};
