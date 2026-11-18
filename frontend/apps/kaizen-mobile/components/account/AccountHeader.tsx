import { ChevronRight } from "lucide-react-native";
import { Platform, Pressable, View } from "react-native";

import { Avatar } from "@/components/ui/avatar";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useLargeText } from "@/components/ui/use-large-text";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

const IS_IOS = Platform.OS === "ios";

type AccountHeaderProps = {
  name: string;
  subtitle: string;
  onPress: () => void;
};

/**
 * The signed-in user at the top of Account: avatar, name, and email with the
 * active organization. The whole card opens Profile. iOS draws it as an inset
 * card with a chevron, Android as a full-bleed row.
 */
export function AccountHeader({ name, subtitle, onPress }: AccountHeaderProps) {
  const { t } = useTranslation();
  const largeText: boolean = useLargeText();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={name}
      accessibilityHint={t("accountOpenProfile")}
      onPress={onPress}
      className={cn(
        "flex-row items-center gap-4 px-4 py-3 active:opacity-70",
        IS_IOS && "mx-4 rounded-lg bg-card"
      )}
    >
      <Avatar name={name} size={56} />
      <View className="flex-1 gap-0.5">
        <Text variant="title-3" numberOfLines={largeText ? undefined : 1}>
          {name}
        </Text>
        {subtitle ? (
          <Text
            variant="footnote"
            className="text-muted-foreground"
            numberOfLines={largeText ? undefined : 1}
          >
            {subtitle}
          </Text>
        ) : null}
      </View>
      {IS_IOS ? (
        <Icon as={ChevronRight} size={18} className="text-muted-foreground opacity-60" />
      ) : null}
    </Pressable>
  );
}
