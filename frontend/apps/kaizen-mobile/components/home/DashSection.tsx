import { ChevronRight, Info } from "lucide-react-native";
import type * as React from "react";
import { Platform, Pressable, View } from "react-native";

import { InlineError } from "@/components/kaizen/EmptyState";
import { Icon } from "@/components/ui/icon";
import { SkeletonGroup, Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";

type DashSectionProps = {
  title: string;
  /** A control at the end of the header, such as the period select or "View roadmap". */
  accessory?: React.ReactNode;
  /** Opens the sheet that explains how the section is counted. */
  onInfo?: () => void;
  infoLabel?: string;
  /**
   * The section body. It spans the card's full width, so rows can draw a
   * full-width pressed state; each body keeps 16 pt of side padding itself.
   */
  children: React.ReactNode;
  className?: string;
};

/**
 * One Home dashboard section: a single bordered card holding the header (title,
 * info button, a trailing control) and the body, so each section reads as one
 * block on the screen ground.
 */
function DashSection({
  title,
  accessory,
  onInfo,
  infoLabel,
  children,
  className,
}: DashSectionProps) {
  return (
    <View className={cn("mx-4 gap-2 rounded-xl border border-border bg-card pb-4 pt-3", className)}>
      <View className="min-h-7 flex-row items-center gap-1.5 px-4">
        <Text
          accessibilityRole="header"
          numberOfLines={1}
          className="shrink font-sans-semibold text-callout text-foreground"
        >
          {title}
        </Text>
        {onInfo && infoLabel ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={infoLabel}
            onPress={onInfo}
            // Grows the 19 pt glyph to the 44 pt (iOS) and 48 dp (Android) touch target.
            hitSlop={Platform.OS === "android" ? 15 : 13}
            className="p-0.5 active:opacity-60"
          >
            <Icon as={Info} size={15} className="text-muted-foreground" />
          </Pressable>
        ) : null}
        <View className="flex-1" />
        {accessory}
      </View>
      {children}
    </View>
  );
}

/** A text link at the end of a section header, such as "View roadmap". */
function DashSectionLink({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel={label}
      onPress={onPress}
      // Grows the 20 pt line to the 44 pt (iOS) and 48 dp (Android) touch target.
      hitSlop={Platform.OS === "android" ? 14 : 12}
      className="flex-row items-center gap-0.5 active:opacity-60"
    >
      <Text variant="subhead" className="text-link" numberOfLines={1}>
        {label}
      </Text>
      <Icon as={ChevronRight} size={14} className="text-link" />
    </Pressable>
  );
}

/** The loading body of a section: `rows` text lines shaped like its content. */
function SectionSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <SkeletonGroup className="gap-3 px-4 py-1">
      {Array.from({ length: rows }, (_, index) => (
        <View key={index} className="flex-row items-center gap-3">
          <Skeleton width={28} height={28} round />
          <View className="flex-1 gap-1.5">
            <Skeleton width="70%" />
            <Skeleton width="40%" height={11} />
          </View>
        </View>
      ))}
    </SkeletonGroup>
  );
}

/** A section that failed to load, with its own retry; the other sections stay. */
function SectionError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <InlineError message={message} onRetry={onRetry} className="mx-4" />;
}

/** The empty body of a section: one short line. */
function SectionEmpty({ message }: { message: string }) {
  return (
    <Text variant="subhead" className="px-4 py-3 text-center text-muted-foreground">
      {message}
    </Text>
  );
}

export { DashSection, DashSectionLink, SectionEmpty, SectionError, SectionSkeleton };
export type { DashSectionProps };
