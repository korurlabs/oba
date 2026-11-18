import { ChevronsUpDown } from "lucide-react-native";
import * as React from "react";
import { Pressable } from "react-native";

import { Avatar } from "@/components/ui/avatar";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";

type OrganizationChipProps = {
  name: string;
  /** Read by VoiceOver, such as "Organization: Korur". */
  accessibilityLabel: string;
  /** Opens the organization switcher. */
  onPress: () => void;
};

/**
 * The active organization under the Boards title on iOS: its avatar, its
 * name and an up-down chevron, as a 32 pt pill that opens the organization
 * switcher. Android switches from the title row's building icon instead.
 */
export function OrganizationChip({ name, accessibilityLabel, onPress }: OrganizationChipProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      hitSlop={{ top: 6, bottom: 6, left: 2, right: 2 }}
      onPress={onPress}
      className="min-h-8 flex-row items-center gap-1.5 self-start rounded-full border border-border bg-card py-1 pl-1 pr-2.5 active:opacity-60"
    >
      <Avatar name={name} size={24} />
      <Text numberOfLines={1} className="shrink font-sans-semibold text-[14px] text-foreground">
        {name}
      </Text>
      <Icon as={ChevronsUpDown} size={14} className="text-muted-foreground" />
    </Pressable>
  );
}
