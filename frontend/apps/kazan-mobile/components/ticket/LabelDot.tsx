import { View } from "react-native";

import { labelColour } from "@/components/kazan/ticketMetaModel";

/** The colour dot of a label; a missing or invalid colour is grey. */
export function LabelDot({ colour }: { colour: string | null | undefined }) {
  const hex: string | null = labelColour(colour);
  return (
    <View
      className={hex ? "size-2.5 rounded-full" : "size-2.5 rounded-full bg-muted-foreground"}
      style={hex ? { backgroundColor: hex } : undefined}
    />
  );
}
