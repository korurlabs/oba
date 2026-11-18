import {
  ArrowRightLeft,
  CalendarRange,
  CircleCheck,
  ExternalLink,
  Flag,
  Inbox,
  Link,
  MessageSquareShare,
  SquareCheck,
  Trash2,
  UserPlus,
  type LucideIcon,
} from "lucide-react-native";

import type { ActionSheetAction } from "@/components/kazan/actionSheetModel";
import type { TicketMenuItem, TicketMenuItemId } from "@/components/ticket/ticketMenuModel";
import type { TranslationKey } from "@/i18n";

const TICKET_MENU_ICONS: Record<TicketMenuItemId, LucideIcon> = {
  open: ExternalLink,
  select: SquareCheck,
  status: CircleCheck,
  assignToMe: UserPlus,
  priority: Flag,
  moveBoard: ArrowRightLeft,
  moveSprint: CalendarRange,
  moveBacklog: Inbox,
  copyLink: Link,
  shareToChat: MessageSquareShare,
  openWeb: ExternalLink,
  delete: Trash2,
};

/** The ticket menu's items as action-sheet rows, each with its label and icon. */
export function ticketMenuActions(
  t: (key: TranslationKey) => string,
  items: readonly TicketMenuItem[]
): ActionSheetAction[] {
  return items.map((item) => ({
    label: t(item.labelKey),
    icon: TICKET_MENU_ICONS[item.id],
    destructive: item.destructive,
    onPress: item.onPress,
  }));
}
