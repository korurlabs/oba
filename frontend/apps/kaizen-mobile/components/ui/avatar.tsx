import * as AvatarPrimitive from "@rn-primitives/avatar";
import * as React from "react";
import { Image, View, type ImageSourcePropType } from "react-native";

import {
  avatarPixels,
  avatarTint,
  initials,
  type AvatarSize,
} from "@/components/ui/avatar-initials";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

type AvatarProps = {
  /** The person or organization; gives the initials, the tint and the accessibility label. */
  name: string;
  /** Photo URL. Without one, or while it loads or after it fails, the fallback shows. */
  src?: string | null;
  /** `sm` 20, `md` 32 (default), `lg` 64, or a number. */
  size?: AvatarSize;
  /**
   * An image shown instead of initials, such as the Korur mark for an
   * organization without a logo.
   */
  fallbackImage?: ImageSourcePropType;
  /** Adds the presence dot: green when online, hollow when offline. */
  online?: boolean;
  className?: string;
};

function Avatar({ name, src, size = "md", fallbackImage, online, className }: AvatarProps) {
  const { t } = useTranslation();
  const pixels = avatarPixels(size);
  const tint = avatarTint(name);
  const dimension = { width: pixels, height: pixels };

  const avatar = (
    <AvatarPrimitive.Root
      alt={name}
      className={cn("shrink-0 overflow-hidden rounded-full", className)}
      style={dimension}
    >
      {src ? <AvatarPrimitive.Image source={{ uri: src }} style={dimension} /> : null}
      <AvatarPrimitive.Fallback
        className={cn(
          "items-center justify-center rounded-full",
          fallbackImage ? "bg-muted" : tint.ground
        )}
        style={dimension}
      >
        {fallbackImage ? (
          <Image source={fallbackImage} style={dimension} accessibilityIgnoresInvertColors />
        ) : (
          <Text
            className={cn("font-sans-semibold", tint.text)}
            style={{ fontSize: Math.max(8, Math.round(pixels * 0.38)) }}
            maxFontSizeMultiplier={1}
          >
            {initials(name)}
          </Text>
        )}
      </AvatarPrimitive.Fallback>
    </AvatarPrimitive.Root>
  );

  if (online == null) return avatar;

  const dot = Math.max(8, Math.round(pixels * 0.3));
  return (
    <View
      style={dimension}
      accessible
      accessibilityLabel={`${name}, ${online ? t("kitOnline") : t("kitOffline")}`}
    >
      {avatar}
      <View
        className={cn(
          "absolute bottom-0 right-0 rounded-full border-2 border-card",
          online ? "bg-success" : "bg-card"
        )}
        style={{ width: dot, height: dot }}
      >
        {online ? null : <View className="flex-1 rounded-full border border-muted-foreground" />}
      </View>
    </View>
  );
}

type AvatarStackProps = {
  people: { name: string; src?: string | null }[];
  /** How many avatars show before the "+N" disc; default 3. */
  max?: number;
  /** How many people there are in all, when `people` lists only some; default its length. */
  total?: number;
  size?: AvatarSize;
  className?: string;
};

/** Overlapping avatars ringed in `card`, then "+N" on `muted`. */
function AvatarStack({ people, max = 3, total, size = "sm", className }: AvatarStackProps) {
  const pixels = avatarPixels(size);
  const shown = people.slice(0, Math.max(0, max));
  const rest = Math.max(total ?? people.length, people.length) - shown.length;
  const overlap = -Math.round(pixels * 0.25);
  const ringed = { width: pixels + 4, height: pixels + 4 };

  return (
    <View
      className={cn("flex-row items-center", className)}
      accessible
      accessibilityLabel={people.map((person) => person.name).join(", ")}
    >
      {shown.map((person, index) => (
        <View
          key={`${person.name}-${index}`}
          className="items-center justify-center rounded-full bg-card"
          style={[ringed, index > 0 ? { marginLeft: overlap } : null]}
        >
          <Avatar name={person.name} src={person.src} size={pixels} />
        </View>
      ))}
      {rest > 0 ? (
        <View
          className="items-center justify-center rounded-full border-2 border-card bg-muted"
          style={[ringed, shown.length > 0 ? { marginLeft: overlap } : null]}
        >
          <Text
            className="font-sans-medium text-muted-foreground"
            style={{ fontSize: Math.max(9, Math.round(pixels * 0.36)) }}
            maxFontSizeMultiplier={1}
          >
            {`+${rest}`}
          </Text>
        </View>
      ) : null}
    </View>
  );
}

export { Avatar, AvatarStack };
export type { AvatarProps, AvatarStackProps };
