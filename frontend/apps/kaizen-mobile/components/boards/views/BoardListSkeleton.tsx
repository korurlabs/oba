import { View } from "react-native";

import { SkeletonCard } from "@/components/ui/skeleton";

/** Ticket cards while a board list loads for the first time. */
function BoardListSkeleton({ count = 3 }: { count?: number }) {
  return (
    <View className="gap-3 px-4 pt-2">
      {Array.from({ length: count }, (_, index) => (
        <SkeletonCard key={index} />
      ))}
    </View>
  );
}

export { BoardListSkeleton };
