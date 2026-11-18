import { View } from "react-native";

import { Skeleton, SkeletonCard, SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";

/** The ticket screen while the ticket loads, laid out like the loaded screen. */
export function TicketDetailSkeleton() {
  return (
    <View className="gap-4 pt-4">
      <SkeletonGroup className="gap-3 px-4">
        <Skeleton width={120} height={14} />
        <Skeleton width="90%" height={24} />
        <Skeleton width="60%" height={24} />
        <View className="flex-row gap-2">
          <Skeleton width={96} height={28} />
          <Skeleton width={80} height={28} />
        </View>
      </SkeletonGroup>
      <View>
        <SkeletonRow />
        <SkeletonRow />
        <SkeletonRow />
      </View>
      <SkeletonCard className="mx-4" />
    </View>
  );
}
