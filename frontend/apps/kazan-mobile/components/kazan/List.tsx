import { Check, ChevronRight, ExternalLink } from "lucide-react-native";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, StyleSheet, View, type AccessibilityRole } from "react-native";

import { Avatar } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { formatBadgeCount } from "@/components/ui/badge-count";
import { CheckboxMark } from "@/components/ui/checkbox";
import { Icon } from "@/components/ui/icon";
import { RadioMark } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import { Text } from "@/components/ui/text";
import { useLargeText } from "@/components/ui/use-large-text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { cn } from "@/lib/utils";

/*
 * Settings-style lists. iOS draws inset grouped sections: `card` cells with
 * `radius-lg` corners on the `screen` ground, hairlines inset to the text and
 * a chevron on rows that open something. Android draws full-bleed 56 dp rows
 * under a `link` coloured header, without chevrons or row dividers.
 */

const IS_IOS = Platform.OS === "ios";

/** Where a row sits in its section; iOS draws no hairline under the last row. */
type ListPosition = "only" | "first" | "middle" | "last";

const ListPositionContext = React.createContext<ListPosition | null>(null);

function positionOf(index: number, count: number): ListPosition {
  if (count === 1) return "only";
  if (index === 0) return "first";
  return index === count - 1 ? "last" : "middle";
}

type ListSectionProps = {
  /** The section title above the rows. */
  header?: string;
  /** Helper text under the rows. */
  footer?: string;
  /**
   * A refusal of the section's input, on its own line under the footer, so
   * the helper text stays readable and the refusal is announced.
   */
  error?: string | null;
  children: React.ReactNode;
  className?: string;
};

/** A group of ListRows. Every direct child is told its position in the group. */
function ListSection({ header, footer, error, children, className }: ListSectionProps) {
  const rows = React.Children.toArray(children).filter(React.isValidElement);
  return (
    <View className={cn(IS_IOS ? "mt-6" : "mt-2", className)}>
      {header ? (
        <Text
          accessibilityRole="header"
          className={cn(
            IS_IOS
              ? "px-8 pb-1.5 font-sans text-footnote uppercase text-muted-foreground"
              : "px-4 pb-2 pt-4 font-sans-medium text-subhead text-link"
          )}
        >
          {header}
        </Text>
      ) : null}
      <View className={cn(IS_IOS && "mx-4 overflow-hidden rounded-lg bg-card")}>
        {rows.map((row, index) => (
          <ListPositionContext.Provider
            key={row.key ?? index}
            value={positionOf(index, rows.length)}
          >
            {row}
          </ListPositionContext.Provider>
        ))}
      </View>
      {footer ? (
        <Text
          className={cn(
            "pt-1.5 font-sans text-footnote text-muted-foreground",
            IS_IOS ? "px-8" : "px-4"
          )}
        >
          {footer}
        </Text>
      ) : null}
      {error ? (
        <Text
          accessibilityRole="alert"
          className={cn(
            "pt-1.5 font-sans text-footnote text-destructive",
            IS_IOS ? "px-8" : "px-4"
          )}
        >
          {error}
        </Text>
      ) : null}
    </View>
  );
}

type ListAccessory = "chevron" | "switch" | "check" | "checkbox" | "radio" | "external" | "none";

/**
 * A command a screen reader offers on the row, such as the X that unpicks a
 * board in a preview. A row reads as one element, so a button drawn inside it
 * is out of VoiceOver's and TalkBack's reach; the action is how they run it.
 */
type ListRowAction = {
  /** A stable name, unique within the row. */
  name: string;
  /** What VoiceOver and TalkBack read out for the action. */
  label: string;
  onAction: () => void;
};

type ListRowProps = {
  title: string;
  subtitle?: string;
  /** `warning` marks a caution, such as the role that keeps a board from being picked. */
  subtitleTone?: "muted" | "destructive" | "success" | "warning";
  /** A value at the trailing edge, such as the chosen language. */
  value?: string;
  /** A leading icon: a coloured tile on iOS, a plain 24 dp icon on Android. */
  icon?: LucideIcon;
  /** The iOS tile ground, such as `bg-destructive`; `bg-primary-fill` by default. */
  iconTone?: string;
  /** The iOS tile ground as a colour value, such as a board's own colour; wins over `iconTone`. */
  iconColor?: string;
  /** A leading avatar; `online` adds the presence dot. */
  avatar?: { name: string; src?: string | null; online?: boolean };
  /** A leading colour dot class, such as a status dot `bg-status-done`. */
  dot?: string;
  /** A leading colour dot of a colour value, such as a label's own colour; wins over `dot`. */
  dotColor?: string;
  /** Any other leading element, such as a TicketTypeBadge. */
  leading?: React.ReactNode;
  /**
   * The trailing control. Default: a chevron when the row has `onPress` (iOS
   * only; Android rows carry none), nothing otherwise.
   */
  accessory?: ListAccessory;
  /** The state of a `switch`, `check`, `checkbox` or `radio` accessory. */
  checked?: boolean;
  /** Called with the new state when a `switch` or `checkbox` row is tapped. */
  onCheckedChange?: (checked: boolean) => void;
  /** Any other trailing element, before the accessory. */
  trailing?: React.ReactNode;
  /** A count badge before the accessory. */
  badge?: number | null;
  /**
   * Messages that notify the reader, such as mentions: an `@N` pill in place
   * of the count badge, so a mention reads apart from a plain unread count.
   */
  mentions?: number | null;
  /** Something new behind the row, such as unread messages: a bold title. */
  unread?: boolean;
  /** The row the reader is in now, such as the open channel: on the `primary-light` tint. */
  active?: boolean;
  onPress?: () => void;
  /** Opens the row's menu; also offered to VoiceOver and TalkBack as the row's long-press action. */
  onLongPress?: () => void;
  /** The commands of buttons inside the row, offered to VoiceOver and TalkBack. */
  actions?: readonly ListRowAction[];
  /** A red title for a destructive command row ("Sign out"). */
  destructive?: boolean;
  /** A field the form cannot be sent without: a `destructive` asterisk after the title. */
  required?: boolean;
  /** A field with an error: the title in `destructive`; the message goes in `subtitle`. */
  invalid?: boolean;
  /** A centred title, for a command row without an icon. */
  center?: boolean;
  disabled?: boolean;
  /** Lets the title and subtitle wrap instead of truncating to one line. */
  wrap?: boolean;
  /** The subtitle's line limit when it does not wrap; default 2. */
  subtitleLines?: number;
  /** Nesting level of a sub-item, such as a sub-ticket: 20 pt per level. */
  indent?: number;
  /** Overrides the position given by ListSection, for a row outside one. */
  position?: ListPosition;
  /** The hairline's left inset on iOS; by default it starts at the text. */
  separatorInset?: number;
  accessibilityHint?: string;
  /** What the row's badges say, read out after its label, such as "2 unread messages". */
  accessibilityValue?: string;
};

const SUBTITLE_TONE = {
  muted: "text-muted-foreground",
  destructive: "text-destructive",
  success: "text-success",
  warning: "text-warning",
} as const;

/** iOS tile 29 pt plus the 12 pt gap; avatars are 32 pt. */
const IOS_LEADING_WIDTH = { tile: 41, avatar: 44, dot: 20 } as const;

function accessibilityRoleOf(
  accessory: ListAccessory,
  pressable: boolean
): AccessibilityRole | undefined {
  if (accessory === "switch") return "switch";
  if (accessory === "checkbox") return "checkbox";
  if (accessory === "radio") return "radio";
  if (accessory === "external") return "link";
  return pressable ? "button" : undefined;
}

/** One row of a ListSection. The whole row is the touch target. */
function ListRow({
  title,
  subtitle,
  subtitleTone = "muted",
  value,
  icon,
  iconTone = "bg-primary-fill",
  iconColor,
  avatar,
  dot,
  dotColor,
  leading,
  accessory,
  checked = false,
  onCheckedChange,
  trailing,
  badge,
  mentions,
  unread = false,
  active = false,
  onPress,
  onLongPress,
  actions,
  destructive = false,
  required = false,
  invalid = false,
  center = false,
  disabled = false,
  wrap = false,
  subtitleLines = 2,
  indent = 0,
  position,
  separatorInset,
  accessibilityHint,
  accessibilityValue,
}: ListRowProps) {
  const colors = useThemeColors();
  // At an accessibility text size one line holds a word or two, so rows wrap
  // and the trailing value moves under the title, where it has the full width
  // and does not squeeze the title into a column of single letters.
  const largeText: boolean = useLargeText();
  const wraps: boolean = largeText || wrap;
  const contextPosition = React.useContext(ListPositionContext);
  const rowPosition = position ?? contextPosition ?? "only";
  const toggles = accessory === "switch" || accessory === "checkbox";
  const handlePress = toggles && !onPress ? () => onCheckedChange?.(!checked) : onPress;
  const pressable = !!handlePress && !disabled;
  const resolvedAccessory: ListAccessory = accessory ?? (onPress && IS_IOS ? "chevron" : "none");
  const mentionText = formatBadgeCount(mentions);
  const countText = mentionText ? `@${mentionText}` : formatBadgeCount(badge);
  const paddingLeft = 16 + Math.max(0, indent) * 20;

  const leadingWidth =
    icon && IS_IOS
      ? IOS_LEADING_WIDTH.tile
      : avatar
        ? IOS_LEADING_WIDTH.avatar
        : dot || dotColor
          ? IOS_LEADING_WIDTH.dot
          : 0;
  const hairlineLeft = separatorInset ?? paddingLeft + leadingWidth;
  const showHairline = IS_IOS && (rowPosition === "first" || rowPosition === "middle");

  const leadingNode = icon ? (
    IS_IOS ? (
      <View
        className={cn("size-[29px] items-center justify-center rounded-[7px]", iconTone)}
        style={iconColor ? { backgroundColor: iconColor } : undefined}
      >
        <Icon as={icon} size={18} className="text-primary-foreground" />
      </View>
    ) : (
      <Icon
        as={icon}
        size={24}
        className={destructive ? "text-destructive" : "text-muted-foreground"}
      />
    )
  ) : avatar ? (
    <Avatar name={avatar.name} src={avatar.src} size="md" online={avatar.online} />
  ) : dotColor ? (
    <View className="size-2 rounded-full" style={{ backgroundColor: dotColor }} />
  ) : dot ? (
    <View className={cn("size-2 rounded-full", dot)} />
  ) : (
    leading
  );

  const accessoryNode = (() => {
    switch (resolvedAccessory) {
      case "chevron":
        return <Icon as={ChevronRight} size={18} className="text-muted-foreground opacity-60" />;
      case "external":
        return <Icon as={ExternalLink} size={16} className="text-muted-foreground" />;
      case "check":
        return checked ? (
          <Icon as={Check} size={20} className={IS_IOS ? "text-link" : "text-primary-fill"} />
        ) : null;
      case "checkbox":
        return <CheckboxMark checked={checked} disabled={disabled} />;
      case "radio":
        return <RadioMark checked={checked} disabled={disabled} />;
      case "switch":
        return (
          <View
            pointerEvents="none"
            importantForAccessibility="no-hide-descendants"
            accessibilityElementsHidden
          >
            <Switch value={checked} disabled={disabled} />
          </View>
        );
      default:
        return null;
    }
  })();

  const titleClassName = cn(
    unread ? "font-sans-semibold text-body" : "font-sans text-body",
    destructive || invalid
      ? "text-destructive"
      : center && IS_IOS
        ? "text-link"
        : "text-foreground",
    center && "text-center",
    // Only the title dims: the subtitle of a disabled row often says why
    // ("Not available yet") and has to stay readable.
    disabled && "opacity-50"
  );

  const content = (
    <>
      {leadingNode ? <View className="items-center justify-center">{leadingNode}</View> : null}
      <View className={cn("flex-1 justify-center gap-0.5", center && "items-center")}>
        <Text numberOfLines={wraps ? undefined : 1} className={titleClassName}>
          {title}
          {required ? <Text className="text-destructive"> *</Text> : null}
        </Text>
        {subtitle ? (
          <Text
            numberOfLines={wraps ? undefined : subtitleLines}
            className={cn("font-sans text-footnote", SUBTITLE_TONE[subtitleTone])}
          >
            {subtitle}
          </Text>
        ) : null}
        {value && largeText ? (
          <Text className="font-sans text-body text-muted-foreground">{value}</Text>
        ) : null}
      </View>
      {value && !largeText ? (
        <Text
          numberOfLines={1}
          className="max-w-[45%] shrink font-sans text-body text-muted-foreground"
        >
          {value}
        </Text>
      ) : null}
      {trailing}
      {countText ? <Badge variant="count">{countText}</Badge> : null}
      {accessoryNode}
      {showHairline ? (
        <View
          className="absolute bottom-0 right-0 bg-border"
          style={{ left: hairlineLeft, height: StyleSheet.hairlineWidth }}
        />
      ) : null}
    </>
  );

  const rowClassName = cn(
    "flex-row items-center gap-3 pr-4",
    IS_IOS ? "min-h-row-ios py-2.5" : subtitle ? "min-h-[72px] py-3" : "min-h-row-android py-2",
    active && "bg-primary-light"
  );
  const label = [title, subtitle, value].filter(Boolean).join(", ");
  const a11yValue = accessibilityValue ? { text: accessibilityValue } : undefined;
  const a11yActions = [
    ...(onLongPress ? [{ name: "longpress" }] : []),
    ...(actions ?? []).map((action) => ({ name: action.name, label: action.label })),
  ];
  const runA11yAction = (actionName: string) => {
    if (actionName === "longpress") onLongPress?.();
    else actions?.find((action) => action.name === actionName)?.onAction();
  };

  if (!handlePress) {
    return (
      <View
        accessible
        accessibilityLabel={label}
        accessibilityValue={a11yValue}
        accessibilityActions={a11yActions.length > 0 ? a11yActions : undefined}
        onAccessibilityAction={
          a11yActions.length > 0
            ? (event) => runA11yAction(event.nativeEvent.actionName)
            : undefined
        }
        className={rowClassName}
        style={{ paddingLeft }}
      >
        {content}
      </View>
    );
  }

  return (
    <Pressable
      accessibilityRole={accessibilityRoleOf(resolvedAccessory, pressable)}
      accessibilityLabel={label}
      accessibilityHint={accessibilityHint}
      accessibilityValue={a11yValue}
      accessibilityState={{
        disabled,
        checked: toggles || resolvedAccessory === "radio" ? checked : undefined,
        selected: active || (resolvedAccessory === "check" ? checked : undefined),
      }}
      accessibilityActions={a11yActions.length > 0 ? a11yActions : undefined}
      onAccessibilityAction={
        a11yActions.length > 0 ? (event) => runA11yAction(event.nativeEvent.actionName) : undefined
      }
      disabled={disabled}
      onPress={handlePress}
      onLongPress={onLongPress}
      android_ripple={{ color: colors.accent }}
      className={cn(rowClassName, IS_IOS && "active:bg-accent")}
      style={{ paddingLeft }}
    >
      {content}
    </Pressable>
  );
}

export { ListRow, ListSection };
export type { ListAccessory, ListPosition, ListRowAction, ListRowProps, ListSectionProps };
