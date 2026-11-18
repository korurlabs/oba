import {
  BookOpen,
  Box,
  Briefcase,
  Bug,
  Building,
  Building2,
  Cloud,
  Code,
  Cpu,
  Database,
  Flag,
  Folder,
  GitBranch,
  Globe,
  Heart,
  House,
  Kanban,
  Layers,
  LayoutDashboard,
  Lock,
  Megaphone,
  Monitor,
  Package,
  Palette,
  Rocket,
  Server,
  Settings,
  Shield,
  ShoppingCart,
  Smartphone,
  Sparkles,
  Star,
  Target,
  Terminal,
  Users,
  Wallet,
  Wrench,
  Zap,
} from "lucide-react-native";
import type { LucideIcon } from "lucide-react-native";

import type { BoardRow } from "@/components/boards/boardModel";
import { boardIconKey } from "@/components/boards/boardRowModel";

/**
 * The board icons the app bundles, by lucide name. Importing every lucide
 * icon for a free-text field would put the whole set in the bundle, so a
 * board whose icon is not listed here falls back to the board glyph.
 */
const BOARD_ICONS: Record<string, LucideIcon> = {
  "book-open": BookOpen,
  box: Box,
  briefcase: Briefcase,
  bug: Bug,
  building: Building,
  "building-2": Building2,
  cloud: Cloud,
  code: Code,
  cpu: Cpu,
  database: Database,
  flag: Flag,
  folder: Folder,
  "git-branch": GitBranch,
  globe: Globe,
  heart: Heart,
  home: House,
  house: House,
  kanban: Kanban,
  layers: Layers,
  "layout-dashboard": LayoutDashboard,
  lock: Lock,
  megaphone: Megaphone,
  monitor: Monitor,
  package: Package,
  palette: Palette,
  rocket: Rocket,
  server: Server,
  settings: Settings,
  shield: Shield,
  "shopping-cart": ShoppingCart,
  smartphone: Smartphone,
  sparkles: Sparkles,
  star: Star,
  target: Target,
  terminal: Terminal,
  users: Users,
  wallet: Wallet,
  wrench: Wrench,
  zap: Zap,
};

/** The glyph of a board without an icon of its own: a branch for a child board. */
export function defaultBoardIcon(row: Pick<BoardRow, "parentId">): LucideIcon {
  return row.parentId ? GitBranch : Kanban;
}

/** The board's own icon when it names a bundled one, else the default glyph. */
export function boardIcon(row: Pick<BoardRow, "icon" | "parentId">): LucideIcon {
  const key: string | null = boardIconKey(row.icon);
  return (key ? BOARD_ICONS[key] : undefined) ?? defaultBoardIcon(row);
}
