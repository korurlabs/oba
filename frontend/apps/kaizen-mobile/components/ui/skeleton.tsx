import * as React from "react";
import { View, type DimensionValue } from "react-native";
import Animated, {
  cancelAnimation,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from "react-native-reanimated";

import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

/** One pulse, dim to bright and back, takes 1.4 s. */
const PULSE_HALF_MS = 700;

type SkeletonProps = {
  width?: DimensionValue;
  height?: DimensionValue;
  /** A circle, for an avatar placeholder. */
  round?: boolean;
  className?: string;
};

/**
 * A `muted` block that pulses while content loads; still under Reduce Motion.
 * Use it for the first load only; a refresh shows pull to refresh instead.
 */
function Skeleton({ width = "100%", height = 14, round = false, className }: SkeletonProps) {
  const reduceMotion = useReducedMotion();
  const opacity = useSharedValue(1);

  React.useEffect(() => {
    if (reduceMotion) {
      opacity.value = 1;
      return;
    }
    opacity.value = withRepeat(withTiming(0.45, { duration: PULSE_HALF_MS }), -1, true);
    return () => cancelAnimation(opacity);
  }, [opacity, reduceMotion]);

  const animatedStyle = useAnimatedStyle(() => ({ opacity: opacity.value }));

  return (
    <Animated.View
      className={cn("bg-muted", round ? "rounded-full" : "rounded-sm", className)}
      style={[{ width, height }, animatedStyle]}
    />
  );
}

/** The loading region: one accessible "Loading" element around placeholders. */
function SkeletonGroup({ children, className }: { children: React.ReactNode; className?: string }) {
  const { t } = useTranslation();
  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={t("kitLoading")}
      accessibilityState={{ busy: true }}
      className={className}
    >
      {children}
    </View>
  );
}

/** Placeholder of a ticket card: key, two title lines, a meta row and an avatar. */
function SkeletonCard({ className }: { className?: string }) {
  return (
    <SkeletonGroup className={cn("gap-2 rounded-lg bg-card p-3", className)}>
      <Skeleton width={80} height={12} />
      <Skeleton width="92%" />
      <Skeleton width="60%" />
      <View className="flex-row items-center justify-between">
        <Skeleton width={56} height={12} />
        <Skeleton width={20} height={20} round />
      </View>
    </SkeletonGroup>
  );
}

/** Placeholder of a list row: an avatar and two text lines. */
function SkeletonRow({ className }: { className?: string }) {
  return (
    <SkeletonGroup
      className={cn("min-h-row-ios flex-row items-center gap-3 px-4 py-2.5", className)}
    >
      <Skeleton width={32} height={32} round />
      <View className="flex-1 gap-1.5">
        <Skeleton width="70%" />
        <Skeleton width="40%" height={11} />
      </View>
    </SkeletonGroup>
  );
}

export { Skeleton, SkeletonCard, SkeletonGroup, SkeletonRow };
